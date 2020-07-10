import org.apache.commons.mail.util.MimeMessageParser;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import javax.activation.DataSource;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.io.IOUtils.toByteArray;

public class CheckingMails {

    public static String sanitizeFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<>]", "_");
    }

    public static void createFolders(List<String> folderNames) {
        new File("E-Mails").mkdirs();

        for (String name :  folderNames) {
            if (name.contains("/")) {
                String[] splitted = name.split("/");

                String dirName = "E-Mails";

                for (int i = 0; i < splitted.length; i++) {
                    dirName += "/" + splitted[i];
                    new File(dirName).mkdirs();
                }
            }
            else {
                new File("E-Mails/" + name).mkdirs();
            }
        }

        System.out.println("Folders are created.");
    }

    public static void traverseFolders(Folder root, List<String> folderNames) {
        folderNames.add(root.getFullName());

        try {
            if (root.list().length > 0) {
                for (Folder folder : root.list()) {
                    traverseFolders(folder, folderNames);
                }
            }
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public static String getHeaderText(Message message) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        MimeMessageParser parser = new MimeMessageParser((MimeMessage) message).parse();

        stringBuilder.append("Subject: ").append(parser.getSubject()).append("\n");
        stringBuilder.append("From: ").append(parser.getFrom()).append("\n");

        stringBuilder.append("To: ");
        List<Address> addresses = parser.getTo();
        for (int i = 0; i < addresses.size(); i++) {
            stringBuilder.append(addresses.get(i).toString());

            if (i != addresses.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append("\n");

        stringBuilder.append("CC: ");
        addresses = parser.getCc();
        for (int i = 0; i < addresses.size(); i++) {
            stringBuilder.append(addresses.get(i).toString());

            if (i != addresses.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append("\n");

        stringBuilder.append("BCC: ");
        addresses = parser.getBcc();
        for (int i = 0; i < addresses.size(); i++) {
            stringBuilder.append(addresses.get(i).toString());

            if (i != addresses.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append("\n");

        stringBuilder.append("Attachments: ");
        List<DataSource> attachments = parser.getAttachmentList();
        for (int i = 0; i < attachments.size(); i++) {
            stringBuilder.append(attachments.get(i).getName());

            if (i != attachments.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append("\n");

        return stringBuilder.toString();
    }

    public static boolean isPlain(Message message) throws Exception {
        return getPlainText(message) != null;
    }

    public static String getPlainText(Message message) throws Exception {
        return new MimeMessageParser((MimeMessage) message).parse().getPlainContent();
    }

    public static String getHTML(Message message) throws Exception {
        return new MimeMessageParser((MimeMessage) message).parse().getHtmlContent();
    }

    public static void writeFile(String filePath, String content) throws IOException {
        OutputStream outputStream = new FileOutputStream(new File(filePath));
        outputStream.write(content.getBytes());
        outputStream.close();

        System.out.println(filePath + " is saved.");
    }

    public static void check(String host, String user, String password) {
        try {
            Properties properties = new Properties();
            properties.put("mail.store.protocol", "imaps");
            Session emailSession = Session.getDefaultInstance(properties);

            Store store = emailSession.getStore("imaps");
            store.connect(host, user, password);

            List<String> folderNames = new ArrayList<>();
            traverseFolders(store.getDefaultFolder(), folderNames);

            for (String folderName : folderNames) {
                System.out.println(">> " + folderName);
            }

            createFolders(folderNames);

            for (String emailFolderName : folderNames) {
                try {
                    Folder emailFolder = store.getFolder(emailFolderName);
                    emailFolder.open(Folder.READ_ONLY);

                    Message[] messages = emailFolder.getMessages();

                    int counter = 1;
                    for (Message message : messages) {
                        String dirName = MessageFormat.format("E-Mails/{0}/[{1}] {2}", emailFolder.getFullName(), counter++, sanitizeFilename(message.getSubject()));

                        new File(dirName).mkdirs();
                        System.out.println(dirName + " is created.");

                        writeFile(dirName + "/header.txt", getHeaderText(message));

                        if (isPlain(message)) {
                            writeFile(dirName + "/message.txt", getPlainText(message));
                        } else {
                            writeFile(dirName + "/message.html", getHTML(message));
                        }

                        if (message.getContentType().contains("multipart") && message.getContent() instanceof Multipart) {
                            Multipart multiPart = (Multipart) message.getContent();

                            for (int j = 0; j < multiPart.getCount(); j++) {
                                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(j);

                                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                                    String fileName = MimeUtility.decodeText(part.getFileName());

                                    System.out.println(dirName + "/" + fileName + " is being saved.");

                                    OutputStream outputStream = new FileOutputStream(new File(dirName + "/" + fileName));
                                    outputStream.write(toByteArray(part.getInputStream()));
                                    outputStream.close();

                                    System.out.println(dirName + "/" + fileName + " is saved.");
                                }
                            }
                        }
                    }

                    emailFolder.close(false);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}