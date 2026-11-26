# Arquitectura

Este documento describe cómo está organizado Kren 1.0. El porqué de cada decisión está
en [decisiones.md](decisiones.md), citada aquí como D-xxx.

## Módulos

```mermaid
flowchart TB
    subgraph app [":app — Android"]
        ui["ui/*<br/>pantallas Compose y ViewModels"]
        cont["Contenedor<br/>inyección manual"]
        rec["recordatorios/<br/>AlarmManager · ReceptorAlarma · ReceptorSistema"]
    end

    subgraph data [":core:data — Android"]
        room["FinanzasDatabase (Room v7)<br/>DAOs y repositorios *Room"]
        resp["Respaldo · CifradoRespaldo<br/>PBKDF2 + AES-GCM"]
    end

    subgraph domain [":core:domain — Kotlin Multiplatform (JVM + JS)"]
        reglas["Reglas<br/>libro · tarjetas · disponible<br/>recurrentes · metas · recordatorios"]
        puertos["Puertos<br/>RepositorioLibro · RepositorioTarjetas<br/>RepositorioRecurrentes · RepositorioMetas<br/>RepositorioRecordatorios"]
    end

    subgraph nube [":feature:nube — solo distribución nube"]
        sesion["login · registro · recuperar"]
        red["catálogo · divisas · cliente REST"]
    end

    ui --> cont
    cont --> room
    cont --> reglas
    room -.->|implementa| puertos
    reglas --> puertos
    rec --> reglas
    app -.->|nubeImplementation| nube
```

| Módulo | Depende de | Regla que lo protege |
|---|---|---|
| `:core:domain` | Solo kotlinx-datetime y coroutines | Compila también para JS, así que no puede importar nada de Android ni de la JVM (D-004) |
| `:core:data` | `:core:domain` (como `api`) | No tiene red. La guardia de dependencias rechaza Firebase y las librerías HTTP |
| `:feature:nube` | Firebase Auth, Credential Manager | No puede depender de `:core:data`, así que nunca lee la base local (D-020) |
| `:app` | Todos los anteriores | La distribución `privada` falla al compilar si aparece el permiso `INTERNET` (D-005) |

## Capas

Kren sigue una arquitectura de puertos y adaptadores ligera:

1. **Dominio** (`:core:domain`): tipos de valor (`Dinero`, `CuentaId`), entidades y
   servicios con las reglas (`Registro`, `ServicioLibro`, `CalendarioTarjeta`,
   `ComprasAMeses`, `LineaDeTiempo`, `PlanRecordatorios`). Las interfaces `Repositorio*` son los puertos.
2. **Datos** (`:core:data`): adaptadores de Room que implementan los puertos. Las
   entidades de Room nunca salen de este módulo, porque los mapeos las convierten en
   tipos del dominio.
3. **Presentación** (`:app`): un `ViewModel` por pantalla expone un `StateFlow` de estado
   inmutable y las pantallas Compose lo dibujan. La navegación es estado propio, sin
   Navigation Compose (D-008).
4. **Composición** (`Contenedor.kt`): crea la base, los repositorios y los servicios una
   sola vez y los entrega a los ViewModels, sin Hilt ni Koin (D-007).

## Flujo: registrar un gasto con tarjeta

```mermaid
sequenceDiagram
    actor U as Usuario
    participant P as PantallaMovimientos
    participant VM as MovimientosViewModel
    participant D as Registro (dominio)
    participant R as RepositorioLibroRoom
    participant DB as Room (SQLite)

    U->>P: monto, categoría, tarjeta, meses
    P->>VM: guardarCaptura()
    VM->>D: Registro.desdeCaptura(...)
    D-->>VM: ResultadoRegistro.Valido(movimiento)<br/>cargo a la categoría, abono a la tarjeta
    VM->>R: registrar(movimiento)
    R->>DB: INSERT movimiento + asientos<br/>en una transacción
    DB-->>R: Flow de saldos actualizado
    R-->>VM: Flow<List<Movimiento>>
    VM-->>P: nuevo estado (saldo y disponible real)
```

Como cada pantalla observa un `Flow` de Room, guardar un gasto actualiza Inicio,
Tarjetas, Análisis y el disponible real sin recargar nada a mano.

## Base de datos (esquema v7)

```mermaid
erDiagram
    cuenta ||--o{ asiento : "recibe"
    movimiento ||--|{ asiento : "se compone de"
    cuenta ||--o| tarjeta : "si es de crédito"
    tarjeta ||--o{ estado_de_cuenta : "captura cada ciclo"
    cuenta ||--o{ recurrente : "categoría y cuenta preferida"
    cuenta ||--o| tope_categoria : "límite mensual"
    meta_ahorro ||--o{ aporte_meta : "historial"

    cuenta {
        long id PK
        string nombre
        string tipo "ACTIVO, PASIVO, GASTO, INGRESO, CAPITAL"
        bool archivada
        int orden
        string icono
    }
    movimiento {
        long id PK
        date fecha
        string descripcion
        instant creado_en
        int meses "null o 3 a 24 (MSI)"
    }
    asiento {
        long id PK
        long movimiento_id FK
        long cuenta_id FK
        long centavos "los asientos suman 0"
    }
    tarjeta {
        long cuenta_id PK
        long linea_centavos
        int dia_corte
        int dia_limite
        double tasa_anual
    }
    estado_de_cuenta {
        long id PK
        long tarjeta_id FK
        date fecha_corte
        date fecha_limite
        long pago_sin_intereses
        long pago_minimo
    }
    recurrente {
        long id PK
        string nombre
        string clase
        long monto_centavos
        long categoria_id FK
        long cuenta_id FK
        date proxima
    }
    tope_categoria {
        long categoria_id PK
        long limite_centavos
    }
    meta_ahorro {
        long id PK
        string nombre
        long objetivo_centavos
        long apartado_centavos
        date fecha_limite
    }
    aporte_meta {
        long id PK
        long meta_id FK
        date fecha
        long centavos
    }
    recordatorio_entregado {
        string clave PK
        string tipo
        instant entregado_en
    }
```

Los esquemas exportados de cada versión (v1 a v7) están en `core/data/schemas/`.
Cada salto tiene una prueba de migración que corre en un dispositivo real (D-012).

## Disponible real

```mermaid
flowchart LR
    saldo["Efectivo y débito hoy"] --> lt
    fijos["Pagos y cobros fijos<br/>próximos 30 días"] --> lt
    tarj["Pagos de tarjeta<br/>estado capturado o estimado<br/>(incluye MSI)"] --> lt
    metas["Metas con<br/>reservar del disponible"] --> lt
    lt["LineaDeTiempo<br/>saldo proyectado día por día"] --> min["Disponible real =<br/>mínimo de los 30 días"]
```

El número que ve el usuario no es el saldo de hoy, sino el punto más bajo al que
llegará el saldo antes de que entre el siguiente ingreso. Así se evita gastar hoy un
dinero que ya está comprometido para la tarjeta (D-017).

## Recordatorios

```mermaid
flowchart LR
    evento["Cambio de datos<br/>o reinicio del teléfono"] --> plan["PlanRecordatorios.calcular<br/>(dominio, puro)"]
    plan --> alarma["AlarmManager inexacto<br/>una sola alarma: la próxima"]
    alarma --> recv["ReceptorAlarma"]
    recv --> aviso["Notificación"]
    recv --> anota["recordatorio_entregado"]
    recv --> plan
```

El plan se recalcula cada vez en lugar de guardar una lista de alarmas. Por eso un
reinicio o un cambio de hora no deja avisos huérfanos (D-015).

## Distribuciones

```mermaid
flowchart TB
    codigo["Mismo código base"] --> privada
    codigo --> nube
    privada["privada<br/>io.github.joako601.finanzas<br/>sin INTERNET"]
    nube["nube<br/>io.github.joako601.finanzas.nube<br/>+ :feature:nube"]
    nube --> fb["Firebase Auth<br/>correo y Google"]
    nube --> api["Backend Spring Boot<br/>(repositorio aparte)"]
```

Las dos se pueden instalar a la vez porque tienen paquetes distintos. La base de datos
financiera nunca sale del teléfono en ninguna de las dos: la sección nube solo maneja la
sesión y el catálogo (D-020, D-021).

## Seguridad en el build

- `gradle/verification-metadata.xml`: SHA-256 de cada artefacto descargado (D-010).
- `gradle/guardia-dependencias.gradle.kts`: lista de grupos permitidos por módulo, para
  que una dependencia de red no se cuele en `:core:*`.
- `settings.gradle.kts`: el repositorio de Google solo sirve grupos de Google y AndroidX,
  para evitar la confusión de dependencias.
- Respaldo en Android desactivado para la base (`backup_rules.xml`,
  `data_extraction_rules.xml`, D-006): el único respaldo es el cifrado con tu contraseña.
