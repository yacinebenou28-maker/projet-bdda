package sgbd;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class DiskManager {

    private String dmDir;
    private int pageSize;

    private RandomAccessFile file;

    private ArrayList<Integer> freePages;



    public void Init(String dmDir, int pageSize) {

        this.dmDir = dmDir;
        this.pageSize = pageSize;

        this.freePages = new ArrayList<>();

        File directory = new File(dmDir);

        // Création du dossier s'il n'existe pas
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File pagesFile = new File(directory, "pages.dat");

        try {

            // Création du fichier s'il n'existe pas
            if (!pagesFile.exists()) {
                pagesFile.createNewFile();
            }

            // Ouverture du fichier
            file = new RandomAccessFile(pagesFile, "rw");

            // Si un fichier de configuration existe,
            // on récupère les anciennes informations
            File configFile = new File(directory, "config.txt");

            if (configFile.exists()) {

                java.util.Scanner scanner =
                        new java.util.Scanner(configFile);

                while (scanner.hasNextLine()) {

                    String line = scanner.nextLine();

                    // Récupération de pageSize
                    if (line.startsWith("pageSize=")) {

                        int savedPageSize =
                                Integer.parseInt(
                                        line.substring("pageSize=".length())
                                );

                        // Vérification de compatibilité
                        if (savedPageSize != pageSize) {

                            System.out.println(
                                "Erreur : pageSize différent de celui sauvegardé."
                            );
                        }
                    }

                    // Récupération des pages libres
                    if (line.startsWith("freePages=")) {

                        String pages =
                                line.substring("freePages=".length());

                        if (!pages.isEmpty()) {

                            String[] numbers =
                                    pages.split(",");

                            for (String number : numbers) {

                                freePages.add(
                                    Integer.parseInt(number)
                                );
                            }
                        }
                    }
                }

                scanner.close();
            }

        } catch (IOException e) {

            e.printStackTrace();
        }
    }



    public void Save() {

        File configFile =
                new File(dmDir, "config.txt");

        try {

            PrintWriter writer =
                    new PrintWriter(configFile);

            // Sauvegarde de la taille d'une page
            writer.println("pageSize=" + pageSize);

            // Sauvegarde des pages libres
            writer.print("freePages=");

            for (int i = 0; i < freePages.size(); i++) {

                writer.print(freePages.get(i));

                if (i < freePages.size() - 1) {
                    writer.print(",");
                }
            }

            writer.println();

            writer.close();

        } catch (IOException e) {

            e.printStackTrace();
        }
    }



    public IPageId AllocPage() {

        // Cas 1 : une page libre existe

        if (!freePages.isEmpty()) {

            int pageNumber =
                    freePages.remove(0);

            return new PageId(pageNumber);
        }


        // Cas 2 : aucune page libre
        // → création d'une nouvelle page

        try {

            // Nombre actuel de pages
            int pageNumber =
                    (int) (file.length() / pageSize);

            // On se place à la fin du fichier
            file.seek(file.length());

            // Création d'une page vide
            byte[] emptyPage =
                    new byte[pageSize];

            // Écriture de la page
            file.write(emptyPage);

            return new PageId(pageNumber);

        } catch (IOException e) {

            e.printStackTrace();

            return null;
        }
    }



    public void ReadPage(
            IPageId ipid,
            ByteBuffer buffer) {

        PageId pageId =
                (PageId) ipid;

        try {

            // Position de la page dans le fichier
            long position =
                    (long) pageId.getPageNumber()
                    * pageSize;

            // On se déplace jusqu'à la page
            file.seek(position);

            // Tableau temporaire
            byte[] data =
                    new byte[pageSize];

            // Lecture du fichier
            file.readFully(data);

            // Copie vers le ByteBuffer
            buffer.put(data);

            // Prépare le buffer pour la lecture
            buffer.flip();

        } catch (IOException e) {

            e.printStackTrace();
        }
    }



    public void WritePage(
            IPageId ipid,
            ByteBuffer buffer) {

        PageId pageId =
                (PageId) ipid;

        try {

            // Position de la page
            long position =
                    (long) pageId.getPageNumber()
                    * pageSize;

            // On se place sur la page
            file.seek(position);

            // Tableau contenant les données
            byte[] data =
                    new byte[pageSize];

            // Copie du ByteBuffer vers le tableau
            buffer.get(data);

            // Écriture dans le fichier
            file.write(data);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }



    public void DeallocPage(
            IPageId ipid) {

        PageId pageId =
                (PageId) ipid;

        int pageNumber =
                pageId.getPageNumber();

        // On vérifie que la page n'est
        // pas déjà dans la liste
        if (!freePages.contains(pageNumber)) {

            freePages.add(pageNumber);
        }
    }
}
