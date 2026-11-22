# Reglas propias de R8 para la app.
#
# Vacío a propósito: Room, Compose y Lifecycle publican sus reglas dentro de sus AAR
# (consumer rules) y R8 las aplica solas. Solo se agrega una regla aquí cuando un
# fallo real del APK de release la exige, con un comentario que explique cuál.

# okio (transitiva de lottie-compose) referencia la anotación de compilación JSR-305
# javax.annotation.Nullable, ausente en tiempo de ejecución en Android; R8 la reporta en release.
-dontwarn javax.annotation.Nullable
