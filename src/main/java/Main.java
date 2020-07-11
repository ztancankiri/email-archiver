import java.util.Scanner;

public class Main {
    public static final String HOST = "imap.bilkent.edu.tr";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.printf("IMAP Host [%s]: ", HOST);
        String temp = scanner.nextLine();
        String input_host = temp.length() > 0 ? temp : HOST;
        System.out.println();

        System.out.print("IMAP Username: ");
        String input_username = scanner.nextLine();
        System.out.println();

        System.out.print("IMAP Password: ");
        String input_password = scanner.nextLine();
        System.out.println();

        EMailArchiver.download(input_host, input_username, input_password);
    }
}