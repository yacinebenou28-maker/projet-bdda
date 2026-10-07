package sgbd;
 
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
 

public class DiskManagerTests {
 
    @FunctionalInterface
    private interface Test {
        void executer(File dossier) throws Exception;
    }
 
    private static int reussis = 0;
    private static int echoues = 0;
 
    // ------------------------------------------------------------------
    // main
    // ------------------------------------------------------------------
 
    public static void main(String[] args) {
 
        // Init / Alloc
        lancer("TestInitDossierVide", DiskManagerTests::testInitDossierVide);
        lancer("TestAllocNumerosDistincts", DiskManagerTests::testAllocNumerosDistincts);
 
        // Lecture / écriture
        lancer("TestPageNeuveRemplieDeZeros", DiskManagerTests::testPageNeuveRemplieDeZeros);
        lancer("TestEcritureLecture (pageSize = 4)", DiskManagerTests::testEcritureLecture);
        lancer("TestEcritureLecture (pageSize = 1)", DiskManagerTests::testEcritureLecturePageSize1);
        lancer("TestEcritureLecture (pageSize = 4096)", DiskManagerTests::testEcritureLectureGrandePage);
        lancer("TestPlusieursPagesIndependantes", DiskManagerTests::testPlusieursPagesIndependantes);
        lancer("TestEcraserUnePage", DiskManagerTests::testEcraserUnePage);
        lancer("TestBufferPretALireApresReadPage", DiskManagerTests::testBufferPretALireApresReadPage);
 
        // Désallocation
        lancer("TestDeallocPuisAlloc", DiskManagerTests::testDeallocPuisAlloc);
        lancer("TestDeallocPlusieursPages", DiskManagerTests::testDeallocPlusieursPages);
        lancer("TestDeallocDeuxFoisLaMemePage", DiskManagerTests::testDeallocDeuxFoisLaMemePage);
 
        // Persistance (Save puis nouveau Init)
        lancer("TestSaveCreeDesFichiers", DiskManagerTests::testSaveCreeDesFichiers);
        lancer("TestPersistanceDonnees", DiskManagerTests::testPersistanceDonnees);
        lancer("TestPersistancePagesLibres", DiskManagerTests::testPersistancePagesLibres);
        lancer("TestPersistanceSansPageLibre", DiskManagerTests::testPersistanceSansPageLibre);
 
        // Ré-initialisation d'un même DiskManager
        lancer("TestReinitAutreDossier", DiskManagerTests::testReinitAutreDossier);
        lancer("TestReinitAutreTaillePage", DiskManagerTests::testReinitAutreTaillePage);
 
        System.out.println();
        System.out.println(reussis + " test(s) réussi(s), " + echoues + " échoué(s).");
 
        if (echoues > 0) {
            System.exit(1);
        }
    }
 
    // ------------------------------------------------------------------
    // Tests : Init / AllocPage
    // ------------------------------------------------------------------
 
    /** Init sur un dossier vide, puis une première allocation. */
    private static void testInitDossierVide(File dossier) {
        DiskManager dm = nouveauDM(dossier, 4);
 
        IPageId p = dm.AllocPage();
 
        verifier(p != null, "AllocPage ne doit pas renvoyer null");
    }
 
    /** Deux allocations successives ne doivent jamais renvoyer la même page. */
    private static void testAllocNumerosDistincts(File dossier) {
        DiskManager dm = nouveauDM(dossier, 4);
 
        Set<Integer> numeros = new HashSet<>();
 
        for (int i = 0; i < 10; i++) {
            IPageId p = dm.AllocPage();
            verifier(p != null, "AllocPage a renvoyé null à l'allocation n°" + i);
            verifier(numeros.add(numero(p)),
                    "La page n°" + numero(p) + " a été allouée deux fois");
        }
    }
 
    // ------------------------------------------------------------------
    // Tests : ReadPage / WritePage
    // ------------------------------------------------------------------
 
    /** Une page tout juste allouée doit être lisible et ne contenir que des 0. */
    private static void testPageNeuveRemplieDeZeros(File dossier) {
        int pageSize = 8;
        DiskManager dm = nouveauDM(dossier, pageSize);
 
        for (int i = 0; i < 3; i++) {
            IPageId p = dm.AllocPage();
            verifierContenu(new byte[pageSize], lire(dm, p, pageSize),
                    "Contenu de la page neuve n°" + i);
        }
    }
 
    /** On écrit puis on relit : on doit retrouver ce qu'on a écrit. */
    private static void testEcritureLecture(File dossier) {
        verifierAllerRetour(dossier, 4);
    }
 
    /** Même chose avec des pages d'un seul octet. */
    private static void testEcritureLecturePageSize1(File dossier) {
        verifierAllerRetour(dossier, 1);
    }
 
    /** Même chose avec une taille de page « réaliste ». */
    private static void testEcritureLectureGrandePage(File dossier) {
        verifierAllerRetour(dossier, 4096);
    }
 
    /** Écrire dans une page ne doit pas modifier les autres pages. */
    private static void testPlusieursPagesIndependantes(File dossier) {
        int pageSize = 4;
        int nbPages = 5;
        DiskManager dm = nouveauDM(dossier, pageSize);
 
        IPageId[] pages = new IPageId[nbPages];
 
        for (int i = 0; i < nbPages; i++) {
            pages[i] = dm.AllocPage();
            ecrire(dm, pages[i], contenu(pageSize, i));
        }
 
        // Relecture dans l'ordre inverse
        for (int i = nbPages - 1; i >= 0; i--) {
            verifierContenu(contenu(pageSize, i), lire(dm, pages[i], pageSize),
                    "Contenu de la page n°" + i);
        }
    }
 
    /** Une seconde écriture sur la même page remplace la première. */
    private static void testEcraserUnePage(File dossier) {
        int pageSize = 4;
        DiskManager dm = nouveauDM(dossier, pageSize);
 
        IPageId p0 = dm.AllocPage();
        IPageId p1 = dm.AllocPage();
 
        ecrire(dm, p0, contenu(pageSize, 1));
        ecrire(dm, p1, contenu(pageSize, 2));
 
        // On écrase p0
        ecrire(dm, p0, contenu(pageSize, 3));
 
        verifierContenu(contenu(pageSize, 3), lire(dm, p0, pageSize), "Page écrasée");
        verifierContenu(contenu(pageSize, 2), lire(dm, p1, pageSize), "Page voisine");
    }
 
    /**
     * Après ReadPage, le buffer doit être « prêt à lire » : position = 0 et
     * pageSize octets restants (le DiskManager fait un flip()).
     */
    private static void testBufferPretALireApresReadPage(File dossier) {
        int pageSize = 4;
        DiskManager dm = nouveauDM(dossier, pageSize);
 
        IPageId p = dm.AllocPage();
        ecrire(dm, p, contenu(pageSize, 7));
 
        ByteBuffer buffer = ByteBuffer.allocate(pageSize);
        dm.ReadPage(p, buffer);
 
        verifierEgal(0, buffer.position(), "position du buffer après ReadPage");
        verifierEgal(pageSize, buffer.remaining(), "octets restants dans le buffer après ReadPage");
    }
 
    // ------------------------------------------------------------------
    // Tests : DeallocPage
    // ------------------------------------------------------------------
 
    /** Une page désallouée doit être réutilisée à l'allocation suivante. */
    private static void testDeallocPuisAlloc(File dossier) {
        DiskManager dm = nouveauDM(dossier, 4);
 
        IPageId p0 = dm.AllocPage();
        IPageId p1 = dm.AllocPage();
        IPageId p2 = dm.AllocPage();
 
        dm.DeallocPage(p1);
 
        IPageId reutilisee = dm.AllocPage();
        verifierEgal(numero(p1), numero(reutilisee), "La page désallouée aurait dû être réutilisée");
 
        // Plus de page libre : il faut maintenant une vraie nouvelle page
        IPageId nouvelle = dm.AllocPage();
        int n = numero(nouvelle);
        verifier(n != numero(p0) && n != numero(p1) && n != numero(p2),
                "Plus aucune page libre : une nouvelle page aurait dû être créée, "
                        + "mais la page n°" + n + " existe déjà");
    }
 
    /** Plusieurs pages libres : toutes doivent être réutilisées avant d'en créer de nouvelles. */
    private static void testDeallocPlusieursPages(File dossier) {
        DiskManager dm = nouveauDM(dossier, 4);
 
        IPageId[] pages = new IPageId[4];
        Set<Integer> numerosExistants = new HashSet<>();
 
        for (int i = 0; i < pages.length; i++) {
            pages[i] = dm.AllocPage();
            numerosExistants.add(numero(pages[i]));
        }
 
        dm.DeallocPage(pages[1]);
        dm.DeallocPage(pages[3]);
 
        Set<Integer> attendus = new HashSet<>();
        attendus.add(numero(pages[1]));
        attendus.add(numero(pages[3]));
 
        Set<Integer> obtenus = new HashSet<>();
        obtenus.add(numero(dm.AllocPage()));
        obtenus.add(numero(dm.AllocPage()));
 
        verifier(attendus.equals(obtenus),
                "Les deux pages libérées auraient dû être réutilisées : attendu "
                        + attendus + " mais obtenu " + obtenus);
 
        int n = numero(dm.AllocPage());
        verifier(!numerosExistants.contains(n),
                "Une nouvelle page aurait dû être créée, mais la page n°" + n + " existe déjà");
    }
 
    /**
     * Désallouer deux fois la même page ne doit pas la rendre allouable deux fois
     * (sinon deux couches du SGBD partageraient la même page !).
     */
    private static void testDeallocDeuxFoisLaMemePage(File dossier) {
        DiskManager dm = nouveauDM(dossier, 4);
 
        dm.AllocPage();
        IPageId p1 = dm.AllocPage();
        dm.AllocPage();
 
        dm.DeallocPage(p1);
        dm.DeallocPage(p1);
 
        int a = numero(dm.AllocPage());
        int b = numero(dm.AllocPage());
 
        verifier(a != b, "La même page n°" + a + " a été allouée deux fois de suite");
    }
 
    // ------------------------------------------------------------------
    // Tests : persistance (Save, puis nouveau DiskManager + Init)
    // ------------------------------------------------------------------
 
    /** Après Save, le dossier de travail doit contenir quelque chose. */
    private static void testSaveCreeDesFichiers(File dossier) {
        DiskManager dm = nouveauDM(dossier, 4);
 
        dm.AllocPage();
        dm.Save();
 
        String[] fichiers = dossier.list();
        verifier(fichiers != null && fichiers.length > 0,
                "Le dossier de travail est vide après Save");
    }
 
    /** Le contenu des pages doit survivre à un « redémarrage » du SGBD. */
    private static void testPersistanceDonnees(File dossier) {
        int pageSize = 4;
 
        DiskManager dm1 = nouveauDM(dossier, pageSize);
        IPageId p0 = dm1.AllocPage();
        IPageId p1 = dm1.AllocPage();
        ecrire(dm1, p0, contenu(pageSize, 1));
        ecrire(dm1, p1, contenu(pageSize, 2));
        dm1.Save();
 
        // « Redémarrage » : nouveau DiskManager sur le même dossier
        DiskManager dm2 = nouveauDM(dossier, pageSize);
 
        verifierContenu(contenu(pageSize, 1), lire(dm2, p0, pageSize), "Page 0 après redémarrage");
        verifierContenu(contenu(pageSize, 2), lire(dm2, p1, pageSize), "Page 1 après redémarrage");
    }
 
    /** La liste des pages libres doit elle aussi survivre au redémarrage. */
    private static void testPersistancePagesLibres(File dossier) {
        int pageSize = 4;
 
        DiskManager dm1 = nouveauDM(dossier, pageSize);
        IPageId p0 = dm1.AllocPage();
        IPageId p1 = dm1.AllocPage();
        IPageId p2 = dm1.AllocPage();
        dm1.DeallocPage(p1);
        dm1.Save();
 
        DiskManager dm2 = nouveauDM(dossier, pageSize);
 
        // La page libérée avant Save doit être réutilisée en premier
        IPageId reutilisee = dm2.AllocPage();
        verifierEgal(numero(p1), numero(reutilisee),
                "La page libre sauvegardée aurait dû être réutilisée après redémarrage");
 
        // Ensuite, une vraie nouvelle page
        int n = numero(dm2.AllocPage());
        verifier(n != numero(p0) && n != numero(p1) && n != numero(p2),
                "Une nouvelle page aurait dû être créée, mais la page n°" + n + " existe déjà");
    }
 
    /** Cas limite : Save avec une liste de pages libres vide, puis redémarrage. */
    private static void testPersistanceSansPageLibre(File dossier) {
        int pageSize = 4;
 
        DiskManager dm1 = nouveauDM(dossier, pageSize);
        IPageId p0 = dm1.AllocPage();
        IPageId p1 = dm1.AllocPage();
        ecrire(dm1, p1, contenu(pageSize, 5));
        dm1.Save();
 
        DiskManager dm2 = nouveauDM(dossier, pageSize);
 
        verifierContenu(contenu(pageSize, 5), lire(dm2, p1, pageSize), "Page 1 après redémarrage");
 
        int n = numero(dm2.AllocPage());
        verifier(n != numero(p0) && n != numero(p1),
                "Une nouvelle page aurait dû être créée, mais la page n°" + n + " existe déjà");
    }
 
    // ------------------------------------------------------------------
    // Tests : Init rappelée sur un DiskManager déjà utilisé
    // ------------------------------------------------------------------
 
    /**
     * Si un DiskManager est rattaché à un autre dossier, ses anciennes données
     * (pages libres, etc.) ne doivent pas « fuiter » dans le nouveau dossier.
     */
    private static void testReinitAutreDossier(File dossier) {
        int pageSize = 4;
        File dossierA = new File(dossier, "A");
        File dossierB = new File(dossier, "B");
        File dossierRef = new File(dossier, "Ref");
 
        // Dossier A : on alloue 3 pages et on en libère une (qui contient des données)
        DiskManager dm = nouveauDM(dossierA, pageSize);
        dm.AllocPage();
        dm.AllocPage();
        IPageId p2 = dm.AllocPage();
        ecrire(dm, p2, contenu(pageSize, 9));
        dm.DeallocPage(p2);
 
        // Même objet, on le rattache au dossier B (vide)
        dm.Init(dossierB.getAbsolutePath(), pageSize);
 
        // Référence : ce que donne un DiskManager tout neuf sur un dossier vide
        int numeroAttendu = numero(nouveauDM(dossierRef, pageSize).AllocPage());
 
        IPageId p = dm.AllocPage();
        verifierEgal(numeroAttendu, numero(p),
                "Après Init sur un dossier vide, la 1ère allocation doit se comporter "
                        + "comme sur un DiskManager neuf (les pages libres de l'ancien dossier "
                        + "doivent être oubliées)");
 
        verifierContenu(new byte[pageSize], lire(dm, p, pageSize),
                "Contenu de la page allouée dans le nouveau dossier");
    }
 
    /** Si on ré-initialise avec une autre taille de page, c'est la nouvelle qui doit s'appliquer. */
    private static void testReinitAutreTaillePage(File dossier) {
        File dossierA = new File(dossier, "A");
        File dossierB = new File(dossier, "B");
 
        DiskManager dm = nouveauDM(dossierA, 4);
        IPageId pA = dm.AllocPage();
        ecrire(dm, pA, contenu(4, 1));
 
        dm.Init(dossierB.getAbsolutePath(), 8);
 
        IPageId pB = dm.AllocPage();
        ecrire(dm, pB, contenu(8, 2));
 
        verifierContenu(contenu(8, 2), lire(dm, pB, 8),
                "Page de 8 octets après ré-initialisation");
    }
 
    // ------------------------------------------------------------------
    // Test générique réutilisé avec plusieurs tailles de page
    // ------------------------------------------------------------------
 
    private static void verifierAllerRetour(File dossier, int pageSize) {
        DiskManager dm = nouveauDM(dossier, pageSize);
 
        IPageId p = dm.AllocPage();
        byte[] ecrit = contenu(pageSize, 1);
 
        ecrire(dm, p, ecrit);

        byte[] lu = lire(dm, p, pageSize);

        // Affichage du contenu écrit puis relu ...         
        System.out.println("          écrit : " + apercu(ecrit));   
        System.out.println("          lu    : " + apercu(lu));     
 
        verifierContenu(ecrit, lire(dm, p, pageSize), "Contenu relu (pageSize = " + pageSize + ")");
    }
 
    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------
 
    /** Crée un DiskManager et l'initialise sur le dossier donné. */
    private static DiskManager nouveauDM(File dossier, int pageSize) {
        DiskManager dm = new DiskManager();
        dm.Init(dossier.getAbsolutePath(), pageSize);
        return dm;
    }
 
    /** Numéro d'une page (seul endroit qui dépend de votre classe PageId). */
    private static int numero(IPageId id) {
        return ((PageId) id).getPageNumber();
    }
 
    /** Écrit un tableau d'octets dans une page (le buffer est prêt à lire : position = 0). */
    private static void ecrire(DiskManager dm, IPageId p, byte[] donnees) {
        dm.WritePage(p, ByteBuffer.wrap(donnees));
    }
 
    /** Lit une page et renvoie son contenu (on crée un buffer neuf à chaque lecture). */
    private static byte[] lire(DiskManager dm, IPageId p, int pageSize) {
        ByteBuffer buffer = ByteBuffer.allocate(pageSize);
        dm.ReadPage(p, buffer);
 
        byte[] resultat = new byte[pageSize];
        buffer.get(resultat);
        return resultat;
    }
 
    /** Génère un contenu reconnaissable (non nul) pour une page, selon une « graine ». */
    private static byte[] contenu(int pageSize, int graine) {
        byte[] donnees = new byte[pageSize];
        for (int j = 0; j < pageSize; j++) {
            donnees[j] = (byte) (graine * 31 + j + 1);
        }
        return donnees;
    }
 
    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
 
    private static void verifierEgal(int attendu, int obtenu, String message) {
        if (attendu != obtenu) {
            throw new AssertionError(message + " : attendu " + attendu + " mais obtenu " + obtenu);
        }
    }
 
    private static void verifierContenu(byte[] attendu, byte[] obtenu, String message) {
        if (!Arrays.equals(attendu, obtenu)) {
            throw new AssertionError(message + " : attendu " + apercu(attendu)
                    + " mais obtenu " + apercu(obtenu));
        }
    }
 
    /** Affichage raccourci d'un tableau (pour ne pas inonder la console avec 4096 octets). */
    private static String apercu(byte[] t) {
        int max = 16;
        if (t.length <= max) {
            return Arrays.toString(t);
        }
        return Arrays.toString(Arrays.copyOf(t, max)) + "... (" + t.length + " octets)";
    }
 
    /** Lance un test avec un dossier temporaire neuf, qu'on supprime ensuite. */
    private static void lancer(String nom, Test test) {
        File dossier = null;
        try {
            dossier = Files.createTempDirectory("dm_tests_").toFile();
            test.executer(dossier);
            System.out.println("[OK]      " + nom);
            reussis++;
        } catch (AssertionError e) {
            System.out.println("[ECHEC]   " + nom + " -> " + e.getMessage());
            echoues++;
        } catch (Exception e) {
            System.out.println("[ERREUR]  " + nom + " -> " + e);
            e.printStackTrace(System.out);
            echoues++;
        } finally {
            if (dossier != null) {
                supprimer(dossier);
            }
        }
    }
 
    private static void supprimer(File f) {
        File[] enfants = f.listFiles();
        if (enfants != null) {
            for (File enfant : enfants) {
                supprimer(enfant);
            }
        }
        f.delete();
    }
}