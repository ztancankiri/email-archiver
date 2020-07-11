import org.apache.commons.mail.util.MimeMessageParser;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import javax.activation.DataSource;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.io.IOUtils.toByteArray;

public class EMailArchiver {

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<>.\\s]", "_");
    }

    private static void createFolders(List<String> folderNames) {
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

    private static void traverseFolders(Folder root, List<String> folderNames) {
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

    private static String getHeaderText(Message message) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        MimeMessageParser parser = new MimeMessageParser((MimeMessage) message).parse();

        stringBuilder.append("Subject: ").append(parser.getSubject()).append("\n");

        stringBuilder.append("Date: ").append(message.getReceivedDate()).append("\n");

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

    private static boolean isPlain(Message message) throws Exception {
        return getPlainText(message) != null;
    }

    private static boolean isHTML(Message message) throws Exception {
        return getHTML(message) != null;
    }

    private static String getPlainText(Message message) throws Exception {
        return new MimeMessageParser((MimeMessage) message).parse().getPlainContent();
    }

    private static String getHTML(Message message) throws Exception {
        return new MimeMessageParser((MimeMessage) message).parse().getHtmlContent();
    }

    private static void writeFile(String filePath, String content) throws IOException {
        OutputStream outputStream = new FileOutputStream(new File(filePath));
        outputStream.write(content.getBytes());
        outputStream.close();

        System.out.println(">> \"" + filePath + "\" is saved.");
    }

    public static void download(String host, String user, String password) {
        try {
            Properties properties = new Properties();
            properties.put("mail.store.protocol", "imaps");
            Session emailSession = Session.getDefaultInstance(properties);

            Store store = emailSession.getStore("imaps");
            store.connect(host, user, password);

            List<String> folderNames = new ArrayList<>();
            traverseFolders(store.getDefaultFolder(), folderNames);

            int counter = 0;

            for (String folderName : folderNames) {
                if (folderName.length() == 0)
                    continue;

                Folder emailFolder = store.getFolder(folderName);
                emailFolder.open(Folder.READ_ONLY);

                Message[] messages = emailFolder.getMessages();
                counter += messages.length;
                emailFolder.close();
            }

            System.out.println(">> E-Mail Count: " + counter);

            createFolders(folderNames);

            counter = 1;

            for (String emailFolderName : folderNames) {
                if (emailFolderName.length() == 0)
                    continue;

                Folder emailFolder = store.getFolder(emailFolderName);
                emailFolder.open(Folder.READ_ONLY);

                Message[] messages = emailFolder.getMessages();

                for (Message message : messages) {
                    String dirName = String.format("E-Mails/%s/[%d] %s", emailFolder.getFullName(), counter++, sanitizeFilename(message.getSubject()));

                    if (new File(dirName).mkdirs()) {
                        System.out.printf(">> \"%s\" is created.%n", dirName);

                        if (isPlain(message)) {
                            writeFile(String.format("%s/%s.txt", dirName, sanitizeFilename(message.getSubject())), String.format("--------------------------------------------------%n%s--------------------------------------------------%n%n%s", getHeaderText(message), getPlainText(message)));
                        } else if (isHTML(message)) {
                            writeFile(String.format("%s/[META] %s.txt", dirName, sanitizeFilename(message.getSubject())), getHeaderText(message));
                            writeFile(String.format("%s/%s.html", dirName, sanitizeFilename(message.getSubject())), getHTML(message));
                        }

                        if (message.getContentType().contains("multipart") && message.getContent() instanceof Multipart) {
                            Multipart multiPart = (Multipart) message.getContent();

                            for (int i = 0; i < multiPart.getCount(); i++) {
                                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(i);

                                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                                    String fileName = MimeUtility.decodeText(part.getFileName());

                                    System.out.printf(">> \"%s/%s\" is being saved.%n", dirName, fileName);

                                    OutputStream outputStream = new FileOutputStream(new File(String.format("%s/%s", dirName, fileName)));
                                    outputStream.write(toByteArray(part.getInputStream()));
                                    outputStream.close();

                                    System.out.printf(">> \"%s/%s\" is saved.%n", dirName, fileName);
                                }
                            }
                        }
                    }
                    else {
                        System.out.printf("[!!!] >> \"%s\" could not be created.%n", dirName);
                    }
                }

                emailFolder.close(false);
            }

            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}