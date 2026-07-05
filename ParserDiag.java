public class ParserDiag {
    public static void main(String[] args) throws Exception {
        // Step 1: can the class even be loaded, and by which classloader?
        Class<?> cls = Class.forName("org.xmlpull.mxp1.MXParserFactory");
        System.out.println("Loaded class OK from: " + cls.getClassLoader());

        // Step 2: try to actually instantiate it - this often reveals the REAL
        // root cause (e.g. NoClassDefFoundError for a missing dependency),
        // which XmlPullParserFactory.newInstance() normally swallows.
        try {
            Object instance = cls.newInstance();
            System.out.println("Instantiated OK: " + instance);
        } catch (Throwable t) {
            System.out.println("Instantiation FAILED - real root cause below:");
            t.printStackTrace();
        }

        // Step 3: try the actual factory API the same way BOE's code does.
        try {
            org.xmlpull.v1.XmlPullParserFactory factory =
                org.xmlpull.v1.XmlPullParserFactory.newInstance(
                    "org.xmlpull.mxp1.MXParserFactory", null);
            System.out.println("Factory created OK: " + factory);
        } catch (Throwable t) {
            System.out.println("Factory creation FAILED - real root cause below:");
            t.printStackTrace();
        }
    }
}
