import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.List;

import com.crystaldecisions.sdk.framework.CrystalEnterprise;
import com.crystaldecisions.sdk.framework.IEnterpriseSession;

import com.sap.sl.sdk.framework.SlContext;
import com.sap.sl.sdk.framework.cms.CmsSessionService;

import com.sap.sl.sdk.authoring.cms.CmsResourceService;
import com.sap.sl.sdk.authoring.local.LocalResourceService;
import com.sap.sl.sdk.authoring.commons.SlResource;
import com.sap.sl.sdk.authoring.datafoundation.DataFoundation;
import com.sap.sl.sdk.authoring.datafoundation.Table;
import com.sap.sl.sdk.authoring.datafoundation.Column;
import com.sap.sl.sdk.authoring.datafoundation.Join;
import com.sap.sl.sdk.authoring.datafoundation.SQLJoin;

/**
 * Retrieves a published .unx universe from the CMS repository, loads its
 * Data Foundation (.dfx) locally, and dumps all tables/columns/joins to
 * universe_datafoundation.csv.
 * <p>
 * Verified against the actual bundled Semantic Layer Authoring SDK Javadoc:
 * - com.sap.sl.sdk.framework.cms.CmsSessionService (CMS session bootstrap)
 * - com.sap.sl.sdk.authoring.cms.CmsResourceService#retrieveUniverse(...)
 * - com.sap.sl.sdk.authoring.local.LocalResourceService#load(...)
 * - com.sap.sl.sdk.authoring.datafoundation.DataFoundation#getTables()/getJoins()
 * </p>
 */
public class DataFoundationExtractor {

    // ==== FILL THESE IN ====
    private static final String CMS_SERVER = "cms:6400"; // host:port for Enterprise session (not the RESTful 6405 port)
    private static final String CMS_USER = "administrator";
    private static final String CMS_PASS = "password";
    private static final String CMS_AUTH = "secEnterprise";

    // Path of the universe within the "Universes" root folder in the CMS repository.
    // Example: "/myCmsFolder/MyUniverse.unx"
    private static final String UNIVERSE_REPOSITORY_PATH = "/Universes/Univers/Fam/test.unx";

    // Local folder where the .blx/.cns/.dfx resources will be downloaded.
    private static final String TARGET_FOLDER = "D:\\client";
    // ========================

    public static void main(String[] args) throws Exception {
        IEnterpriseSession session = CrystalEnterprise.getSessionMgr().logon(CMS_USER, CMS_PASS, CMS_SERVER, CMS_AUTH);
        Thread.currentThread().setContextClassLoader(DataFoundationExtractor.class.getClassLoader());
        SlContext context = SlContext.create();
        try {
            context.getService(CmsSessionService.class).setSession(session);

            CmsResourceService cmsResourceService = context.getService(CmsResourceService.class);
            LocalResourceService localResourceService = context.getService(LocalResourceService.class);

            // Downloads .blx/.cns/.dfx locally; returns the path of the created .blx file.
            String blxPath = cmsResourceService.retrieveUniverse(UNIVERSE_REPOSITORY_PATH, TARGET_FOLDER, true);
            System.out.println("Retrieved business layer at: " + blxPath);

            // The .dfx (Data Foundation) is created alongside the .blx in the same
            // temporary folder. Locate it by scanning that folder instead of
            // guessing the exact file name.
            File blxFile = new File(blxPath);
            File tempFolder = blxFile.getParentFile();
            File[] dfxFiles = tempFolder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".dfx");
                }
            });

            if (dfxFiles == null || dfxFiles.length == 0) {
                throw new RuntimeException("No .dfx file found next to " + blxPath
                        + " - this may be a mono-source universe with the data foundation embedded in the .blx, "
                        + "or retrieveUniverse did not create one. Check the folder: " + tempFolder);
            }

            String dfxPath = dfxFiles[0].getAbsolutePath();
            System.out.println("Found data foundation at: " + dfxPath);

            SlResource resource = localResourceService.load(dfxPath);
            DataFoundation dataFoundation = (DataFoundation) resource;

            try (PrintWriter out = new PrintWriter("universe_datafoundation.csv")) {
                out.println("Table,Column,DataType");
                List<Table> tables = dataFoundation.getTables();
                for (Table table : tables) {
                    System.out.println("Table: " + table.getName());
                    List<Column> columns = table.getColumns();
                    for (Column column : columns) {
                        out.println(table.getName() + "," + column.getName());
                    }
                }

                out.println();
                out.println("LeftTable,RightTable,Expression");
                List<Join> joins = dataFoundation.getJoins();
                for (Join join : joins) {
                    if (join instanceof SQLJoin) {
                        SQLJoin sqlJoin = (SQLJoin) join;
                        String leftTable = sqlJoin.getLeftTable() != null ? sqlJoin.getLeftTable().getName() : "";
                        String rightTable = sqlJoin.getRightTable() != null ? sqlJoin.getRightTable().getName() : "";
                        String expression = sqlJoin.getExpression();
                        System.out.println("Join: " + leftTable + " -> " + rightTable + " : " + expression);
                        out.println(leftTable + "," + rightTable + "," + expression);
                    }
                }
            }

            localResourceService.close(dataFoundation);
            System.out.println("Done. See universe_datafoundation.csv");

        } finally {
            context.close();
            session.logoff();
        }
    }
}