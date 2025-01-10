package com.example;


import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import org.w3c.dom.*;
import java.util.zip.GZIPOutputStream;



public class Main {
    private static final String CERT_PATH = "caminho/para/certificado.pfx";
    private static final String CERT_PASSWORD = "certificatePassword";
    private static final String REQUEST_URI = "https://sefin.nfse.gov.br/sefinnacional/nfse";
    private static final String XML_FILE_PATH = "caminho/para/nfse.xml";


    public static void main(String[] args) {
        try {
            byte[] signedXml = signXml(XML_FILE_PATH, CERT_PATH, CERT_PASSWORD);
            byte[] gzipCompressed = compressGzip(signedXml);
            String base64SignedXml = Base64.getEncoder().encodeToString(gzipCompressed);
            sendToSefin(base64SignedXml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] signXml(String xmlFilePath, String pfxFilePath, String pfxPassword) throws Exception {
        KeyStore keyStore = loadKeyStore(pfxFilePath, pfxPassword);
        String alias = getAlias(keyStore);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, pfxPassword.toCharArray());
        Certificate certificate = keyStore.getCertificate(alias);

        Document doc = loadXmlDocument(xmlFilePath);
        Element infDPS = getInfDPSElement(doc);
        String elementId = getElementId(infDPS);

        XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM");
        SignedInfo signedInfo = createSignedInfo(signatureFactory, elementId);
        KeyInfo keyInfo = createKeyInfo(signatureFactory, certificate);

        Element parentDPS = (Element) infDPS.getParentNode();
        DOMSignContext signContext = new DOMSignContext(privateKey, parentDPS);
        signContext.setNextSibling(infDPS.getNextSibling());

        XMLSignature signature = signatureFactory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(signContext);

        return convertDocumentToByteArray(doc);
    }

    private static KeyStore loadKeyStore(String pfxFilePath, String pfxPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream keyStoreData = new FileInputStream(pfxFilePath)) {
            keyStore.load(keyStoreData, pfxPassword.toCharArray());
        }
        return keyStore;
    }

    private static String getAlias(KeyStore keyStore) throws Exception {
        Enumeration<String> aliases = keyStore.aliases();
        return aliases.hasMoreElements() ? aliases.nextElement() : null;
    }

    private static Document loadXmlDocument(String xmlFilePath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.parse(new FileInputStream(xmlFilePath));
    }

    private static Element getInfDPSElement(Document doc) {
        NodeList infDPSList = doc.getElementsByTagNameNS("http://www.sped.fazenda.gov.br/nfse", "infDPS");
        if (infDPSList.getLength() == 0) {
            throw new IllegalArgumentException("Elemento infDPS não encontrado no XML.");
        }
        Element infDPS = (Element) infDPSList.item(0);
        infDPS.setIdAttribute("Id", true);
        return infDPS;
    }

    private static String getElementId(Element infDPS) {
        String elementId = infDPS.getAttribute("Id");
        if (elementId == null || elementId.isEmpty()) {
            throw new IllegalArgumentException("ID do elemento infDPS não encontrado.");
        }
        return elementId;
    }

    private static SignedInfo createSignedInfo(XMLSignatureFactory signatureFactory, String elementId) throws Exception {
        DigestMethod digestMethod = signatureFactory.newDigestMethod(DigestMethod.SHA1, null);
        SignatureMethod signatureMethod = signatureFactory.newSignatureMethod(SignatureMethod.RSA_SHA1, null);
        CanonicalizationMethod canonicalizationMethod = signatureFactory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null);
        Transform envelopedTransform = signatureFactory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null);
        Transform canonicalizationTransform = signatureFactory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null);

        Reference reference = signatureFactory.newReference("#" + elementId, digestMethod,
                java.util.Arrays.asList(envelopedTransform, canonicalizationTransform), null, null);

        return signatureFactory.newSignedInfo(canonicalizationMethod, signatureMethod, java.util.Collections.singletonList(reference));
    }

    private static KeyInfo createKeyInfo(XMLSignatureFactory signatureFactory, Certificate certificate) {
        KeyInfoFactory keyInfoFactory = signatureFactory.getKeyInfoFactory();
        X509Data x509Data = keyInfoFactory.newX509Data(java.util.Collections.singletonList(certificate));
        return keyInfoFactory.newKeyInfo(java.util.Collections.singletonList(x509Data));
    }

    private static byte[] convertDocumentToByteArray(Document doc) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(outputStream));
        return outputStream.toByteArray();
    }

    public static byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(data);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static void sendToSefin(String base64SignedXml) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            Path certPath = Paths.get(CERT_PATH);
            try (FileInputStream certInputStream = new FileInputStream(certPath.toFile())) {
                keyStore.load(certInputStream, CERT_PASSWORD.toCharArray());
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, CERT_PASSWORD.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            String jsonBody = "{\"dpsXmlGZipB64\": \"" + base64SignedXml + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REQUEST_URI))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Response Body: " + response.body());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}