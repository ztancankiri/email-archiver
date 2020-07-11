import org.apache.commons.mail.util.MimeMessageParser;

import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.apache.commons.io.IOUtils.toByteArray;

public class FolderDownloader extends Thread {

    private Store store;
    private String folderName;
    private int start;
    private int end;
    private Queue<Integer> idQueue;

    public FolderDownloader(Store store, String folderName, int start, int end) {
        this.store = store;
        this.folderName = folderName;
        this.start = start;
        this.end = end;

        idQueue = new LinkedList<>();

        for (int i = start; i <= end; i++) {
            idQueue.add(i);
        }
    }

    @Override
    public void run() {
        try {
            download(store, folderName);
        } catch (Exception e) {
            System.out.println("[!!!]: EXCEPTION ON " + folderName);
            e.printStackTrace();
            FolderDownloader downloader = new FolderDownloader(store, folderName, start, end);
            downloader.start();
            try {
                downloader.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<>.\\s]", "_");
    }

    private String getHeaderText(Message message) throws Exception {
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

    private boolean isPlain(Message message) throws Exception {
        return getPlainText(message) != null;
    }

    private boolean isHTML(Message message) throws Exception {
        return getHTML(message) != null;
    }

    private String getPlainText(Message message) throws Exception {
        return new MimeMessageParser((MimeMessage) message).parse().getPlainContent();
    }

    private String getHTML(Message message) throws Exception {
        return new MimeMessageParser((MimeMessage) message).parse().getHtmlContent();
    }

    private void writeFile(String filePath, String content) throws IOException {
        OutputStream outputStream = new FileOutputStream(new File(filePath));
        outputStream.write(content.getBytes());
        outputStream.close();

        System.out.println(">> \"" + filePath + "\" is saved.");
    }

    private void download(Store store, String folderName) throws Exception {
        Folder emailFolder = store.getFolder(folderName);
        emailFolder.open(Folder.READ_ONLY);

        Message[] messages = emailFolder.getMessages();

        for (Message message : messages) {
            String subjectFileName = sanitizeFilename(message.getSubject());
            String dirName = String.format("E-Mails/%s/[%d] %s", emailFolder.getFullName(), idQueue.poll(), subjectFileName).trim();

            if (new File(dirName).mkdirs()) {
                System.out.printf(">> \"%s\" is created.%n", dirName);

                if (isPlain(message)) {
                    writeFile(String.format("%s/%s.txt", dirName, subjectFileName), String.format("--------------------------------------------------%n%s--------------------------------------------------%n%n%s", getHeaderText(message), getPlainText(message)));
                } else if (isHTML(message)) {
                    writeFile(String.format("%s/[META] %s.txt", dirName, subjectFileName), getHeaderText(message));
                    writeFile(String.format("%s/%s.html", dirName, subjectFileName), getHTML(message));
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
}
