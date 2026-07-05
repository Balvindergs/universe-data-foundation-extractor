# Universe Data Foundation Extractor

Java utilities to connect to a SAP BusinessObjects (BOE) CMS repository, retrieve a
published `.unx` universe, load its Data Foundation locally via the Semantic Layer
(SL) Authoring SDK, and dump all tables/columns/joins to a CSV file.

- `DataFoundationExtractor.java` — main extraction script.
- `ParserDiag.java` — small diagnostic helper used to isolate XML parser
  (`org.xmlpull`) classloading issues independently of the main script.

## Prerequisites

- Eclipse IDE (any recent version) with a JDK 8 configured.
- Access to a SAP BusinessObjects Enterprise XI 4.x installation (used here from
  `D:\Program Files (x86)\SAP BusinessObjects\SAP BusinessObjects Enterprise XI 4.0`).
  Adjust all paths below to match your own install location/drive.

## 1. Create the Eclipse project

1. **File → New → Java Project**, name it e.g. `DataFoundationExtractor`.
2. Copy `DataFoundationExtractor.java` (and `ParserDiag.java`, optional) into the
   project's `src` folder.

## 2. Add the required BOE/SL SDK jars to the Build Path

Right-click the project → **Properties → Java Build Path → Libraries tab →
Add External JARs...**, and add jars from these three folders:

| Folder | Purpose |
|---|---|
| `SAP BusinessObjects Enterprise XI 4.0\SL SDK\java\external\*.jar` | SL SDK third-party dependencies (all jars in the folder) |
| `SAP BusinessObjects Enterprise XI 4.0\SL SDK\eclipse\plugins\*.jar` | SL SDK core OSGi bundles, e.g. `com.sap.sl.sdk.authoring.jar`, `com.sap.sl.sdk.framework.jar` (all jars in the folder) |
| `SAP BusinessObjects Enterprise XI 4.0\java\lib\*.jar` | Enterprise (Crystal) SDK jars, e.g. `cesession.jar`, `cecore.jar`, `celib.jar`, `cdsframeworkutils.jar` (all jars in the folder) |

Add **all** jars from each of the three folders rather than cherry-picking — the
SDK's manifest declares a long chain of `Require-Bundle` dependencies
(`org.eclipse.core.runtime`, `org.eclipse.emf.ecore`, etc.), and adding jars
piecemeal tends to surface one missing class at a time.

### ⚠️ Remove duplicate/old-version jars

The three folders above contain **overlapping and sometimes conflicting versions**
of the same libraries (older jars kept for backward compatibility alongside newer
ones). Having two different versions of the same package on the classpath causes:

```
java.lang.SecurityException: class "..."'s signer information does not match
signer information of other classes in the same package
```

After adding all jars, **remove the older-version duplicates** of these packages
(keep only the newer version of each, no matter which folder it comes from):

- `org.eclipse.emf.common` — remove `_2.4.0.v200902171115.jar`, keep the `2.15.0.*` version
- `org.eclipse.emf.ecore` — remove `_2.4.2.v200902171115.jar`, keep the `2.16.0.*` version
- `org.eclipse.emf.ecore.xmi` — remove `_2.4.1.v200902171115.jar`, keep the `2.15.0.*` version
- `org.eclipse.equinox.common` — remove `_3.4.0.v20080421-2006.jar`, keep the `3.10.200.*` version
- `org.eclipse.equinox.registry` — remove `_3.4.0.v20080516-0950.jar`, keep the `3.8.200.*` version
- `org.eclipse.osgi` — remove `_3.4.3.R34x_v20081215-1030.jar`, keep the `3.13.200.*` version

Also make sure you do **not** have both `xpp3*.jar`/`org.xmlpull.jar` from
`java\external` **and** `org.xmlpull_1.1.4.C.jar` from `eclipse\plugins` at the
same time — this pair also causes a signer conflict. Prefer the plain
`org.xmlpull.jar` from `SL SDK\java\external`.

If you hit a similar `SecurityException` for a different package, find every jar
across the three folders that contains the offending class and remove the
older-version copy the same way. Example (PowerShell), searching for a class:

```powershell
$jarExe = (Get-ChildItem -Path "D:\Program Files (x86)\SAP BusinessObjects" -Recurse -Filter "jar.exe" | Select-Object -First 1).FullName
Get-ChildItem -Path "D:\Program Files (x86)\SAP BusinessObjects\SAP BusinessObjects Enterprise XI 4.0" -Recurse -Filter "*.jar" | ForEach-Object {
    $found = & $jarExe tf $_.FullName 2>$null | Select-String "org/eclipse/core/runtime/IStatus.class"
    if ($found) { Write-Output $_.FullName }
}
```

### If Eclipse still shows resolved-but-broken classes

If class errors persist even after fixing the Build Path:

1. Project → **Clean...** (rebuild).
2. Right-click project → **Close Project**, then reopen it.
3. Restart Eclipse if needed — the Java model cache can get stale after manual
   `.classpath` edits.

## 3. Configure the script

Open `DataFoundationExtractor.java` and fill in the placeholders at the top:

```java
private static final String CMS_SERVER = "<CMS_HOST>:6400"; // Enterprise session port, not 6405 (RESTful)
private static final String CMS_USER = "<CMS_USER>";
private static final String CMS_PASS = "<CMS_PASSWORD>";
private static final String UNIVERSE_REPOSITORY_PATH = "<REPOSITORY_PATH_TO_UNX>"; // e.g. /Universes/MyFolder/MyUniverse.unx
private static final String TARGET_FOLDER = "C:\\Workspace"; // where .blx/.cns/.dfx get downloaded
```

Do **not** commit real credentials back to source control — prefer environment
variables, a local untracked properties file, or a secrets manager.

## 4. Run

Right-click `DataFoundationExtractor.java` → **Run As → Java Application**.

The script logs on to the CMS, retrieves the universe's business layer and data
foundation, and writes `universe_datafoundation.csv` listing all
tables/columns and joins.

### Where the CSV ends up

The script writes to a relative path (`universe_datafoundation.csv`), so it's
created in the **working directory** of the run — by default, Eclipse uses the
project's root folder (same level as `src`, e.g.
`<workspace>\DataFoundationExtractor\universe_datafoundation.csv`). You can
change this in **Run → Run Configurations → Arguments tab → Working
Directory**. After running, right-click the project → **Refresh** (F5) so
Eclipse picks up the newly created file.

## Troubleshooting parser/classloader issues

If you see errors related to `org.xmlpull.mxp1.MXParserFactory` (e.g. "class
not found" or `SecurityException` around `org.xmlpull.v1.XmlSerializer`), run
`ParserDiag.java` standalone (**Run As → Java Application**). It isolates
whether the XML pull-parser class can be loaded/instantiated independently of
BOE's session bootstrap, which swaps in its own classloader during logon. If
`ParserDiag` succeeds but the main script still fails, ensure this line is
present right after `logon(...)` and before creating the `SlContext`:

```java
Thread.currentThread().setContextClassLoader(DataFoundationExtractor.class.getClassLoader());
```
