package cl.gob.digital.firma.firmaXml;

import com.google.gson.Gson;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SpringBootApplication
public class FirmaXmlApplication {
    //Variables de ambiente a configurar.
    public static final String ENTITY = System.getenv("ENTITY");
    public static final String API_TOKEN_KEY = System.getenv("API_TOKEN_KEY");
    public static final String RUN = System.getenv("RUN");
    public static final String PURPOSE = System.getenv("PURPOSE");
    public static final String SECRET_KEY = System.getenv("SECRET_KEY");
    public static final String ENDPOINT_API = System.getenv("ENDPOINT_API");

    //Ruta local del archivo a firmar
    public static final String XML_PATH = "/Users/sfuentealba/Documents/xml_prueba.xml";
    //Ejemplo incluido en resources
    public static final String PIE_FIRMA_PATH = "pie_firma.xml";

    public static void main(String[] args) {
        SpringApplication.run(FirmaXmlApplication.class, args);


        try {
            // crear un nuevo archivo igual al original y guardarlo en PDF_FOLDER_PATH
            Path originalPath = Paths.get(XML_PATH);
            Path parentDirectoryPath = originalPath.getParent();
            String originalFileName = originalPath.getFileName().toString();
            String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String newFileName = currentDateTime + "-" + originalFileName;
            Path newFilePath = parentDirectoryPath.resolve(newFileName);

            if (Files.exists(newFilePath)) {
                throw new IOException("El archivo ya existe: " + newFilePath);
            }
            Files.copy(originalPath, newFilePath);

            try {
                // obtener hash en base64 del archivo para checksum
                byte[] fileContent = Files.readAllBytes(newFilePath);

                String base64 = Base64.getEncoder().encodeToString(fileContent);

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(
                        base64.getBytes(StandardCharsets.UTF_8));
                String checkSum = Base64.getEncoder().encodeToString(encodedhash);

                //Agregar un pie de firma es opcional, se a incorporado solo para muestra, puede omitir este paso
                String pieFirma = getPieFirma();

                // llamar al endpoint para firmar el hash y obtener la respuesta
                String contentResponse = callEndpointToSign(base64, originalFileName, checkSum, pieFirma);

                // decodificar el hash firmado
                System.out.println("Decodificando el content firmado");
                byte[] decodeArr = java.util.Base64.getDecoder().decode(contentResponse);

                // escribir el archivo firmado en la ruta PDF_PATH_END
                String PDF_PATH_END = newFilePath.toString().replace(".xml", "-firmado.xml");
                System.out.println("El documento se genero exitosamente en: " + PDF_PATH_END);
                FileOutputStream fos = new FileOutputStream(PDF_PATH_END);
                fos.write(decodeArr);
                fos.close();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (
                Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static String callEndpointToSign(String contentb64, String name, String checksum, String pieFirma) throws IOException {
        System.out.println("Inicio de llamada al endpoint de FirmaGob");

        // Crear el jwt para el token
        String token = createJWT();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("token", token);
        requestBody.put("api_token_key", API_TOKEN_KEY);

        // crear archivo, se debe agregar uno por cada archivo que se envia

        List<String> xmlObjects = new ArrayList<>();
        xmlObjects.add(pieFirma);

        List<String> nodos = new ArrayList<>();
        nodos.add("#nodo123");// id del nodo donde se incorpora la firma(se debe anteponer #)

        Map<String, Object> archivo1 = new HashMap<>();
        archivo1.put("content-type", "application/xml");
        archivo1.put("content", contentb64);
        archivo1.put("description", name);
        archivo1.put("checksum", checksum);
        archivo1.put("xmlObjects", xmlObjects);
        archivo1.put("references",nodos);

        // crear lista de archivos y agrega todos los archivos a firmar
        List<Map<String, Object>> archivos = new ArrayList<>();
        archivos.add(archivo1);

        // setear lista de archivos al requestBody
        requestBody.put("files", archivos);

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        URL urlObj = new URL(ENDPOINT_API);
        HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        OutputStream os = con.getOutputStream();
        byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
        int statusCode = con.getResponseCode();

        // Leer la respuesta
        System.out.println("Leyendo respuesta del endpoint");
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        if (statusCode == 200) {
            System.out.println("Llamada al endpoint exitosa, se retornara el content del archivo xml firmado");

            Gson gsonResponse = new Gson();
            ResponseToJson jsonResponse = gsonResponse.fromJson(response.toString(), ResponseToJson.class);

            return jsonResponse.getFiles()[0].getContent();
        } else {
            System.out.println("Error al llamar al endpoint");
        }

        con.disconnect();
        return null;
    }

    public static String createJWT() {
        // Crear el JWT
        String token = null;
        // agregamos 5 minuros a la fecha actual, esto permite que la fecha del token siempre sea valida
        String expiration_date_time = LocalDateTime.now().plusMinutes(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        try {
            String jwtToken = Jwts.builder()
                    .claim("entity", ENTITY)
                    .claim("run", RUN)
                    .claim("expiration", expiration_date_time)
                    .claim("purpose", PURPOSE)
                    .setIssuedAt(new Date())
                    .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes(StandardCharsets.UTF_8))
                    .compact();

            System.out.println("Token generado: " + jwtToken);
            token = jwtToken;
        } catch (Exception e) {
            e.printStackTrace();
            //Invalid Signing configuration / Couldn't convert Claims.
        }
        return token;
    }

    private static File getFile(String path) throws URISyntaxException {
        return new File(Objects.requireNonNull(FirmaXmlApplication.class.getClassLoader()
                .getResource(path)).toURI());
    }

    /**
     * ejemplo xmlObject
     * @return String que contiene xml
     */
    private static String getPieFirma() {
        try {
            //Leemos el template xml del layout
            File layout = getFile(PIE_FIRMA_PATH);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(layout);
            return docToString(doc);

        } catch (Exception e) {
            System.out.println("Error al generar Layout");
            e.printStackTrace();
        }

        return null;
    }

    /**
     * convertir xml a string
     */
    private static String docToString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();

        } catch (Exception ex) {
            throw new RuntimeException("Error al convertir a String", ex);
        }
    }

    @Getter
    @Setter
    static class ResponseToJson {
        private FileModel[] files;
        private Metadata metadata;
        private long idSolicitud;
    }

    @Getter
    @Setter
    static class Metadata {
        private boolean otpExpired;
        private int filesSigned;
        private int signedFailed;
        private int objectsReceived;

    }

    @Getter
    @Setter
    static class FileModel {
        private String content;
        private String status;
        private String description;
        private String contentType;
        private String documentStatus;
        private String checksum_original;

    }
}
