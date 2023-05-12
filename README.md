# firma-xml-ejemplo
Ejemplo para la integración con API firmaGob que permite firmar archivo en formato XML

En el ejemplo se muestra como firmar un archivo xml, incorporando además información adicional, realizar la petición a la API creando la estructura requerida para el body, además un ejemplo para generar JWT y como procesar la respuesta de la API.

## Variables de ambiente necesarias

- ENTITY: Código asociado a la institución a la cual pertenece el titular (string), para ambiente de test usamos "Subsecretaría General de la Presidencia"
- API_TOKEN_KEY: Campo no encriptado de tipo string que contiene el código único generado a partir del registro de la aplicación, para ambiente de test usamos "sandbox"
- RUN: Run identificador del titular de fima, no debe contener puntos, guión ni tampoco el dígito verificador (string). Para ambiente de test usamos "22222222"
- PURPOSE: Código asociado al tipo de certificado a utilizar (string) para ambiente de test usamos "Desatendido"
- SECRET_KEY: Secreto para jwtToken, para ambiente de test usamos "27a216342c744f89b7b82fa290519ba0"
- ENDPOINT_API: URL de la api, para ambiente de test usamos https://api.firma.cert.digital.gob.cl/firma/v2/files/tickets

## Consideraciones
- El archivo [pie_firma.xml] es un ejemplo de información adicional a incorporar en la fima mediante xmlObjects definido en la API
