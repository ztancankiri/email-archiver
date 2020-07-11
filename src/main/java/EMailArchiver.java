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

    public static void download(String host, String user, String password) {
        try {
            Properties properties = new Properties();
            properties.put("mail.store.protocol", "imaps");
            Session emailSession = Session.getDefaultInstance(properties);

            Store store = emailSession.getStore("imaps");
            store.connect(host, user, password);

            List<String> folderNames = new ArrayList<>();
            traverseFolders(store.getDefaultFolder(), folderNames);

            List<FolderDownloader> downloaders = new ArrayList<>();
            int counter = 1;

            for (String folderName : folderNames) {
                if (folderName.length() == 0)
                    continue;

                Folder emailFolder = store.getFolder(folderName);
                emailFolder.open(Folder.READ_ONLY);

                Message[] messages = emailFolder.getMessages();
                downloaders.add(new FolderDownloader(store, folderName, counter, counter + messages.length - 1));

                counter += messages.length;
                emailFolder.close();
            }

            System.out.println(">> E-Mail Count: " + counter);
            createFolders(folderNames);

            for (FolderDownloader downloader : downloaders) {
                downloader.start();
            }

            for (FolderDownloader downloader : downloaders) {
                downloader.join();
            }

            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}