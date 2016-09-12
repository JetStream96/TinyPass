package tinypass;

import org.w3c.dom.*;
import java.util.*;
import javax.xml.parsers.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.nio.charset.StandardCharsets;
import static java.lang.System.console;
import static java.lang.System.out;
import static tinypass.Util.*;
import tinypass.Encryption.*;

public class Cli {
    private static final String fileName = "data.xml";

    public static void init(){
        File f = new File(fileName);
        if(f.exists()) {
            out.println("Password database already exists.");
            return;
        }

        out.print("Enter the master password: ");
        char[] password = console().readPassword();
        out.print("Verify the master password: ");
        char[] passwordVerify = console().readPassword();

        if(!Arrays.equals(password, passwordVerify)){
            out.println("The passwords do not match.");
            return;
        }

        byte[] salt = Encryption.getSalt();
        byte[] hash = Encryption.getHash(password, salt);
        Arrays.fill(password, '\0');
        Arrays.fill(passwordVerify, '\0');


        try{
            Document doc = getNewDoc();
            Element passwordInfo = (Element)doc.getDocumentElement()
                .getElementsByTagName("masterPassword").item(0);

            passwordInfo.getElementsByTagName("salt").item(0)
                .setNodeValue(toStringBase64(salt));
            passwordInfo.getElementsByTagName("hash").item(0)
                .setNodeValue(toStringBase64(hash));

            writeToFile(fileName, xmlToString(getNewDoc()));
        }
        catch(Exception ex) {
            out.println("Failed to create password database.");
        }
    }

    private static DocumentBuilder getDocBuilder(){
        return unchecked(() ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder());
    }

    /**
     * Creates and returns a new password database. The structure is like:
     * <root>
     *     <masterPassword>
     *         <salt></salt>
     *         <hash></hash>
     *     </masterPassword>
     *
     *     <data>
     *
     *     </data>
     * </root>
     */
    private static Document getNewDoc() throws Exception{
        Document doc = getDocBuilder().newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);

        Element masterPasswordInfo = doc.createElement("masterPassword");
        masterPasswordInfo.appendChild(doc.createElement("salt"));
        masterPasswordInfo.appendChild(doc.createElement("hash"));

        Element data = doc.createElement("data");
        root.appendChild(masterPasswordInfo);
        root.appendChild(data);

        return doc;
    }

    /**
     * Read the previously saved password database. Returns null if failed.
     */
    private static Document readDoc(){
        File inputFile = new File(fileName);

        try {
            return getDocBuilder().parse(inputFile);
        } catch(Exception ex){
            out.println("Failed to read database.");
            return null;
        }
    }

    private static String xmlToString(Document doc) throws Exception{
        Transformer tr = TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty(OutputKeys.METHOD, "xml");
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        StringWriter sw = new StringWriter();
        tr.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    /**
     * Add an password entry to the existing database.
     */
    public static void addEntry(){
        char[] masterPassword = checkPassword();
        if(masterPassword == null) return;

        out.print("Enter a unique name: ");
        String name = console().readLine();
        out.print("Enter description: ");
        String description = console().readLine();
        out.print("Enter the password: ");
        char[] password = console().readPassword();

        try {
            EncryptResult desResult = Encryption.encrypt(password, description);
            EncryptResult passResult = Encryption.encrypt(password, password.toString());

            Document doc = addNodeEntry(desResult, passResult, name);



        } catch (Exception ex){
            out.println("Failed to add entry to database.");
        }
    }

    /**
     * Reads the password database from file, and add a password entry to the parsed document.
     */
    private static Document addNodeEntry(EncryptResult desResult,
                                         EncryptResult passResult,
                                         String name){
        Document doc = readDoc();
        Element dataNode = (Element)doc.getDocumentElement().getElementsByTagName("data").item(0);
        Element elem = doc.createElement("item");

        Element desNode = convertToElem(doc, desResult, "description");
        Element passNode = convertToElem(doc, passResult, "password");
        Element nameNode = doc.createElement("name");
        nameNode.setNodeValue(name);

        appendChild(elem, desNode, passNode, nameNode);
        dataNode.appendChild(elem);
        return doc;
    }

    private static Element convertToElem(Document doc, EncryptResult item, String name){
        Element e = doc.createElement(name);

        Element iv = doc.createElement("iv");
        iv.setNodeValue(toStringBase64(item.iv));

        Element salt = doc.createElement("salt");
        iv.setNodeValue(toStringBase64(item.salt));

        Element cipherText = doc.createElement("cipherText");
        cipherText.setNodeValue(toStringBase64(item.ciphertext));

        e.appendChild(iv);
        e.appendChild(salt);
        e.appendChild(cipherText);
        return e;
    }

    /**
     * Ask user for the master password. Returns the master password if user
     * entered correctly. Otherwise returns null.
     */
    private static char[] checkPassword(){
        Document doc = readDoc();
        if(doc == null) {
            out.println("Failed to read database.");
            return null;
        }

        Element passwordInfo = (Element)doc.getDocumentElement()
            .getElementsByTagName("masterPassword").item(0);

        byte[] salt = passwordInfo.getElementsByTagName("salt").item(0)
            .getNodeValue()
            .getBytes(StandardCharsets.UTF_8);

        byte[] hash = passwordInfo.getElementsByTagName("hash").item(0)
            .getNodeValue()
            .getBytes(StandardCharsets.UTF_8);

        out.print("Enter the master password: ");
        char[] password = console().readPassword();
        byte[] enteredHash = Encryption.getHash(password, salt);

        if(Arrays.equals(hash, enteredHash)) return password;
        out.println("The master password is incorrect");
        return null;
    }

    public static void newEntry(){

    }
}
