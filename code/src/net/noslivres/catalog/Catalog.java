package net.noslivres.catalog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Catalog {

  static DateFormat df = new SimpleDateFormat ("yyyy-MM-dd");
  static DateFormat df2 = new SimpleDateFormat ("dd/MM/yyyy");
  static DateFormat gallicaDf = new SimpleDateFormat ("yyyy/MM/dd");

  static public class Entry implements Comparable<Entry> {
    public String id;
    public String author;
    public String title;
    public Date creationDate;
    public Date modificationDate;
    public String url;
    public String site;

    public Entry (String auteur, String titre, Date creationDate, Date modificationDate, String url, String site, String id) {
      this.author = auteur.trim ();
      this.title = titre.trim ();
      this.creationDate = creationDate;
      this.modificationDate = modificationDate == null ? creationDate : modificationDate;
      this.url = url.trim ();
      this.site = site.trim ();
      this.id = id;
    }

    public String oneStringForJSON (String s) {
      s = s.replace ("&", "&amp;")
        .replace ("\\", "\\\\")
        .replace ("\"", "\\\"")
        .replace("\r\n", "<br>")
        .replace("\r", "<br>")
        .replace("\n", "<br>")
        .trim ();

      s = "\"" + s + "\"";
      return s;
    }

    public String oneStringForAtom (String s) {
      s = s.replace ("&", "&amp;").replace("\n", "<br/>").replace ("<br>", "<br/>").trim ();
      return s;
    }

    public String oneStringForSQL (String s) {
      s = s.replace ("&", "&amp;").replace("\n", "<br>").replace ("'", "''").trim ();

      s = "'" + s + "'";
      return s;
    }

    public String oneStringForCSV (String s) {
      return '"' + s.replace ("&", "&amp;").replace ("\"", "\\\"").replace("\n", "<br>").trim () + '"';
    }

   public String toJSON (boolean includeDates) {
      return "[" + oneStringForJSON (title) + ","
          + oneStringForJSON (author) + ","
          + (includeDates ? (oneStringForJSON (creationDate == null ? "" : df.format (creationDate)) + ","
                             + oneStringForJSON (modificationDate == null ? "" : df.format (modificationDate)) + ",")
                          : "")
          + oneStringForJSON ("<a href='" + url + "'>" + site + "</a>")
          + "]";
    }

    public String toSQL () {
      return "INSERT INTO livres (titre, auteur, parution, maj, site, url, mots)"
          + " SELECT "
          + oneStringForSQL (title) + ", "
          + oneStringForSQL (author) + ", "
          + oneStringForSQL (creationDate == null ? "" : df.format (creationDate)) + ", "
          + oneStringForSQL (modificationDate == null ? "" : df.format (modificationDate)) + ", "
          + oneStringForSQL (site) + ", "
          + oneStringForSQL (url) + ", "
          + oneStringForSQL (title + " " + author + " " + site) + ";";
    }

    public String toCSV () {
      return
          oneStringForCSV (title) + ","
        + oneStringForCSV (author) + ","
        + oneStringForCSV (creationDate == null ? "" : df.format (creationDate)) + ","
        + oneStringForCSV (modificationDate == null ? "" : df.format (modificationDate)) + ","
        + oneStringForCSV (site) + ","
        + oneStringForCSV (url) + ","
        + oneStringForCSV (title + " " + author + " " + site);
    }

    public String toAtom (Date recent) {
      String status = "Nouveauté";
      Date date = creationDate;
//      if (   creationDate != null
//          && modificationDate != null
//          && ! creationDate.equals (modificationDate)) {
//        status = "Mise à jour";
//        date = modificationDate; }

      if (date == null) {
        return null; }

      if (date.before (recent)) {
        return null; }

      String id = Integer.toString ((title + author).hashCode ());

      return "<entry>"
          + "<id>" + id + "</id>"
          + "<title>" + oneStringForAtom (status + " chez " + site + " : " + author + " - " + title).replaceAll ("<br/>", " ") + "</title>"
          + "<updated>" + date + "</updated>"
          + "<content type='xhtml'><div xmlns='http://www.w3.org/1999/xhtml'>"
            + "<p>" + status + " disponible chez <a href='" + oneStringForAtom (url) + "'>" + site + "</a>.</p>"
            + "<p></p>"
            + "<p><i>" + oneStringForAtom (author) + "</i></p>"
            + "<p>" + oneStringForAtom (title) + "</p>"
          + "</div></content></entry>";
    }

    @Override
    public boolean equals (Object o) {
      if (! (o instanceof Entry)) {
        return false; }
      Entry other = (Entry) o;
      return title.equals(other.title) && author.equals (other.author) && url.equals (other.url);
    }

    @Override
    public int hashCode () {
      return title.hashCode() + author.hashCode() + url.hashCode();
    }

    @Override
    public int compareTo (Entry other) {
      int x = title.compareTo (other.title);
      if (x != 0) return x;
      x = author.compareTo (other.author);
      if (x != 0) return x;
      x = url.compareTo (other.url);
      return x;
    }

    public String toString () {
      return title + " / " + author + " / " + url;
    }
  }

  public static PrintStream consoleOut;

  public SortedSet<Entry> entries = new TreeSet<Entry> ();

  public static URI makeURI (String s, boolean local) throws Exception {
    if (local) {
      int slash = s.lastIndexOf ('/');
      return new File (s.substring (slash + 1)).toURI (); }
    else {
      return new URI (s); }
  }

  //----------------------------------------------------------- from CSV ---

  public String removeQuotes (String s) {
    int l = s.length ();

    if (l >= 2 && s.charAt (0) == '"' && s.charAt (l - 1) == '"') {
      s = s.substring(1, l - 1); }
    return s.replaceAll ("\"\"", "\"");
  }

  public Date parseDate (String s) {
    try {
      return df.parse (s); }
    catch (java.text.ParseException e) {}

    try {
      return df2.parse (s); }
    catch (java.text.ParseException e) {}

    try {
      return gallicaDf.parse (s); }
    catch (java.text.ParseException e) {}

    return null;
  }

  public int collectFromCSV (URI uri, String site) throws Exception {
    int count = 0;
    InputStream s = null;

    try {
      HttpURLConnection.setFollowRedirects (true);
      URLConnection conn = uri.toURL ().openConnection();
//      conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 4.01; Windows NT)");
//      conn.setReadTimeout (30*1000);
      s = conn.getInputStream ();

      LineNumberReader lnr = new LineNumberReader (new InputStreamReader (s, "UTF-16"));
      String line;
      int authorField, titleField;

      line = lnr.readLine ();   // ligne d'en-tete
      String [] fields = line.split ("\t");

      if (fields.length < 3 || 5 < fields.length) {
        System.err.println ("Erreur : moins de trois (" + fields.length + ") colonnes dans la ligne " + lnr.getLineNumber());
        System.err.println (line);
        throw new Exception (); }

      if ("auteur".equals (fields[0].toLowerCase().trim())) {
        authorField = 0;
        titleField = 1; }
      else {
        authorField = 1;
        titleField = 0; }

      while ((line = lnr.readLine ()) != null) {
        Date creationDate = null, modificationDate = null;
        if (line.length () == 0) {
          continue; }
        fields = line.split ("\t");
        if (fields.length < 3) {
          continue; }
        if (fields.length >= 4 && fields [3].length () >= 10) {
          creationDate = parseDate (fields [3].substring (0, 10)); }
        if (fields.length >= 5 && fields [4].length () >= 10) {
          modificationDate = parseDate (fields [4].substring (0, 10)); }
        else {
          modificationDate = creationDate; }

        String id = site + lnr.getLineNumber ();
        Entry e = new Entry (removeQuotes (fields [authorField]),
                             removeQuotes (fields [titleField]),
                             creationDate,
                             modificationDate,
                             removeQuotes (fields [2]),
                             site,
                             id);
        entries.add (e);
        count++; }

      consoleOut.println (site + " : " + count);
      return count; }

    catch (Exception e) {
      consoleOut.println (site + " : 0");
      consoleOut.println (e);
      e.printStackTrace ();
      return count; }

    finally {
      if (s != null) {
        s.close (); } }
  }

  public CharSequence readURI (URI uri) throws Exception {
      StringBuilder sb = new StringBuilder();
      InputStream s = uri.toURL ().openStream ();
      InputStreamReader r = new InputStreamReader (s, "UTF-8");
      char buf[] = new char [4096];

      while (r.read (buf) != -1) {
        sb.append (buf);
      }
      r.close ();
      s.close ();
      return sb;
  }


  public int indirectCollectFromCSV (URI uri, String pattern, String site) throws Exception {

    try {
      CharSequence urlContent = readURI (uri);
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher (urlContent);
      if (m.find ()) {
        URI uri2 = uri.resolve (m.group ());
        return collectFromCSV (uri2, site); }}

    catch (Exception e) {
      consoleOut.println (site + " : 0");
      consoleOut.println (e);
      return 0;
    }

    consoleOut.println (site + " : 0");
    consoleOut.println ("Cannot find pattern");
    return 0;
  }

  //---------------------------------------------------------- from OPDS ---

  public enum OPDSLinks {
    FOLLOW_ALL_LINKS,
    FOLLOW_NAVIGATION_LINKS_ONLY
  }

  private class OPDSReader extends DefaultHandler {

    static private final String ATOM_URI = "http://www.w3.org/2005/Atom";
    static private final String DC_URI = "http://purl.org/dc/elements/1.1/";

    public int count = 0;
    public String site = "";
    private String defaultURL = "";

    public Queue<URI> toVisit = new LinkedList<URI> ();
    public SortedSet<Entry> myEntries = new TreeSet<Entry> ();

    public URI contextURI;

    public OPDSLinks followLinks = OPDSLinks.FOLLOW_NAVIGATION_LINKS_ONLY;

    private boolean inEntry = false;
    private boolean inTitle = false;
    private boolean inUpdated = false;
    private boolean inAuthor = false;
    private boolean inAuthorName = false;
    private boolean inUrl = false;
    private boolean isBook = false;

    private StringBuffer title = new StringBuffer ();
    private StringBuffer updated = new StringBuffer ();
    private StringBuffer authorName = new StringBuffer ();
    private StringBuffer url = new StringBuffer ();

    public OPDSReader (String site, String defaultURL) {
      this.site = site;
      this.defaultURL = defaultURL;
    }

    public OPDSReader followAllLinks () {
      this.followLinks = OPDSLinks.FOLLOW_ALL_LINKS;
      return this;
    }

    @Override
    public void characters (char[] ch, int start, int length) throws SAXException {
      if (inTitle) {
        title.append (ch, start, length); }
      else if (inUpdated) {
        updated.append (ch, start, length); }
      else if (inAuthorName) {
        authorName.append (ch, start, length); }
      else if (inUrl) {
        url.append (ch, start, length); }
    }

    public boolean checkURI (String wanted, String actual) {
      return wanted.equals (actual);
    }

    @Override
    public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {

      if (   checkURI (ATOM_URI, uri) && "link".equals (localName)
          && (OPDSLinks.FOLLOW_ALL_LINKS == followLinks
              ? "application/atom+xml".equals (attributes.getValue ("type"))
              : "application/atom+xml;profile=opds-catalog;kind=navigation".equals (attributes.getValue ("type")))
          && (   "next".equals (attributes.getValue ("rel"))
              || null == attributes.getValue ("rel"))) {
        try {
          toVisit.add (contextURI.resolve (attributes.getValue ("href"))); }
        catch (Exception e) {
          consoleOut.println (e);
          consoleOut.println ("-- failed to add " + attributes.getValue ("href")); }}


      if (checkURI (ATOM_URI, uri) && "entry".equals (localName)) {
        inEntry = true;
        isBook = false;
        url.setLength (0);
        title.setLength (0);
        updated.setLength (0);
        authorName.setLength (0); }

      if (inEntry) {

        if (checkURI (ATOM_URI, uri) && "link".equals (localName)
            && "alternate".equals (attributes.getValue ("rel"))) {
          isBook = true;
          url.setLength (0);
          url.append (attributes.getValue ("href").trim()); }

        if (checkURI (ATOM_URI, uri) && "link".equals (localName)
            && "http://opds-spec.org/acquisition".equals (attributes.getValue ("rel"))) {
          isBook = true;
        }

        if (checkURI (ATOM_URI, uri) && "title".equals (localName)) {
          inTitle = true;
          title.setLength (0); }

        if (checkURI (ATOM_URI, uri) && "updated".equals (localName)) {
          inUpdated = true;
          updated.setLength (0); }

        if (checkURI (ATOM_URI, uri) && "author".equals (localName)) {
          inAuthor = true; }

        if (checkURI (DC_URI, uri) && "source".equals (localName)) {
          inUrl = true;
          url.setLength (0); }}

      if (inAuthor) {
        if (checkURI (ATOM_URI, uri) && "name".equals (localName)) {
          inAuthorName = true;
          authorName.setLength (0); }}
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

      if (checkURI (ATOM_URI, uri) && "entry".equals (localName)) {
        inEntry = false;

        Date updatedDate = null;
        if (updated.length () != 0) {
          updatedDate = parseDate (updated.toString ().trim ()); }

        if (isBook && url.length() == 0) {
          url.append (defaultURL);
        }

        if (url.length() != 0) {
          Entry e = new Entry (authorName.toString (),
                               title.toString (),
                               null,
                               updatedDate,
                               url.toString(),
                               site,
                               url.toString().trim());
          entries.add (e);
          myEntries.add (e);
          count++; }
        return; }

      if (inEntry) {
        if (inAuthorName) {
          if (checkURI (ATOM_URI, uri) && "name".equals (localName)) {
            inAuthorName = false;
            return; }}

        if (checkURI (ATOM_URI, uri) && "title".equals (localName)) {
          inTitle = false;
          return; }

        if (checkURI (ATOM_URI, uri) && "updated".equals (localName)) {
          inUpdated = false;
          return; }

        if (checkURI (ATOM_URI, uri) && "author".equals (localName)) {
          inAuthor = false;
          return; }

        if (checkURI (DC_URI, uri) && "source".equals (localName)) {
          inUrl = false;
          return; }}
    }
  }

  public int collectFromOPDS (URI uri, OPDSReader opdsReader) throws Exception {

    opdsReader.toVisit.add (uri);

    while (opdsReader.toVisit.peek () != null) {
      opdsReader.contextURI = opdsReader.toVisit.remove ();
      // consoleOut.println ("  fetching " + opdsReader.contextURI);

      URLConnection connection = opdsReader.contextURI.toURL().openConnection(Proxy.NO_PROXY);
      connection.setRequestProperty ("User-Agent", "curl/8.7.1");
      InputStream s;

      int countBefore = opdsReader.count;

      try {
        s = connection.getInputStream ();

        try {
          SAXParserFactory spf = SAXParserFactory.newInstance ();
          spf.setNamespaceAware (true);
          spf.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, false);
          spf.setValidating (false);

          SAXParser sp = spf.newSAXParser ();
          sp.parse (new InputSource (s), opdsReader); }
        catch (Exception e) {
          consoleOut.println ("  exception parsing " + opdsReader.contextURI + " " + e.getMessage()); }
        finally {
          s.close (); }}

      catch (Exception e) {
        consoleOut.println ("  error getting " + opdsReader.contextURI
                            + "  " + e.getMessage ()); }

      // Gallica has a 'next' link on the last page of the catalog
      if (opdsReader.count == countBefore && opdsReader.toVisit.size () == 1) {
        opdsReader.toVisit.remove (); }}

    consoleOut.println (opdsReader.site + " : " + opdsReader.myEntries.size ());
    return opdsReader.myEntries.size ();
  }


  //------------------------------------------------------- from Old RDF ---

//  private abstract class OldRDFReader extends DefaultHandler {
//
//    static private final String PGTERMS_URI = "http://www.gutenberg.org/rdfterms/";
//    static private final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
//    static private final String DC_URI = "http://purl.org/dc/elements/1.1/";
//    static private final String DCTERMS_URI = "http://purl.org/dc/terms/";
//
//    public int count = 0;
//    public String site = "";
//
//    abstract protected String id2url (String id);
//
//
//    private boolean inEtext = false;
//    private boolean inTitle = false;
//    private boolean inAuthor = false;
//    private boolean grabText = false;
//    private boolean inLanguage = false;
//    private boolean inCreated = false;
//    private boolean inFile = false;
//    private boolean inModified = false;
//
//    private StringBuffer title = new StringBuffer ();
//    private StringBuffer author = new StringBuffer ();
//    private StringBuffer language = new StringBuffer ();
//    private StringBuffer created = new StringBuffer ();
//    private StringBuffer modified = new StringBuffer ();
//    private String id;
//    private String modifiedId;
//
//
//    public OldRDFReader (String site) {
//      this.site = site;
//    }
//
//    @Override
//    public void characters (char[] ch, int start, int length) throws SAXException {
//
//      if (inLanguage) {
//        language.append (ch, start, length); }
//      else if (inCreated) {
//        created.append (ch, start, length); }
//      else if (inModified) {
//        modified.append (ch, start, length); }
//      else if (! grabText) {
//        return; }
//      else if (inTitle && grabText) {
//        title.append (ch, start, length); }
//      else if (inAuthor && grabText) {
//        author.append (ch, start, length); }
//    }
//
//    @Override
//    public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {
//
//
//      grabText = false;
//
//      if (PGTERMS_URI.equals (uri) && "etext".equals (localName)) {
//        inEtext = true;
//        language.setLength (0);
//        title.setLength (0);
//        author.setLength (0);
//        created.setLength (0);
//        id = attributes.getValue (RDF_URI, "ID").substring (5); }
//
//      if (PGTERMS_URI.equals (uri) && "file".equals (localName)) {
//        inFile = true;
//        modified.setLength (0); }
//
//      if (inEtext) {
//        if (DC_URI.equals (uri) && "language".equals (localName)) {
//          inLanguage = true;
//          language.setLength (0); }
//
//        if (DC_URI.equals (uri) && "title".equals (localName)) {
//          inTitle = true;
//          title.setLength (0); }
//
//        if (DC_URI.equals (uri) && "creator".equals (localName)) {
//          inAuthor = true;
//          author.setLength (0); }
//
//        if (DC_URI.equals (uri) && "created".equals (localName)) {
//          inCreated = true;
//          created.setLength (0); }
//
//        if (RDF_URI.equals (uri) && "li".equals (localName)) {
//          if (inAuthor && author.length() > 0) {
//            author.append ("<br>"); }
//          else if (inTitle && title.length () > 0) {
//            title.append ("<br>"); }}}
//
//      if (inFile) {
//        if (DCTERMS_URI.equals (uri) && "modified".equals (localName)) {
//          inModified = true;
//          modified.setLength (0); }
//
//        if (DCTERMS_URI.equals (uri) && "isFormatOf".equals (localName)) {
//          modifiedId = attributes.getValue (RDF_URI, "resource").substring (6); }}
//
//      grabText = "Literal".equals (attributes.getValue (RDF_URI, "parseType"));
//    }
//
//    @Override
//    public void endElement(String uri, String localName, String qName) throws SAXException {
//
//      grabText = false;
//
//      if (PGTERMS_URI.equals (uri) && "etext".equals (localName)) {
//        inEtext = false;
//        if ("fr".equals (language.toString ())) {
//          Date d;
//          try {
//            d = df.parse (created.toString ().substring (0, 10)); }
//          catch (ParseException ex) {
//            throw new SAXException (ex); }
//          Entry e = new Entry (author.toString (), title.toString (), d, d, id2url (id), site, id);
//          entries.add (e);
//          entriesById.put (id, e);
//          count++; }
//        return; }
//
//      if (PGTERMS_URI.equals (uri) && "file".equals (localName)) {
//        inFile = false;
//        Entry e = entriesById.get (modifiedId);
//        Date modificationDate;
//        try {
//          modificationDate = df.parse (modified.toString ().substring (0, 10)); }
//        catch (ParseException ex) {
//          throw new SAXException (ex); }
//        if (e != null) {
//          if (modificationDate.after (e.modificationDate)) {
//            e.modificationDate = modificationDate; }}
//        return; }
//
//      if (inEtext) {
//        if (DC_URI.equals (uri) && "title".equals (localName)) {
//          inTitle = false;
//          return; }
//
//        if (DC_URI.equals (uri) && "creator".equals (localName)) {
//          inAuthor = false;
//          return; }
//
//        if (DC_URI.equals (uri) && "language".equals (localName)) {
//          inLanguage = false;
//          return; }
//
//        if (DC_URI.equals (uri) && "created".equals (localName)) {
//          inCreated = false;
//          return; }}
//
//      if (inFile) {
//        if (DCTERMS_URI.equals (uri) && "modified".equals (localName)) {
//          inModified = false;
//          return; }}
//    }
//  }
//
//  private class GutenbergOldRDFReader extends OldRDFReader {
//    public GutenbergOldRDFReader (String site) {
//      super (site);
//    }
//
//    protected String id2url (String id) {
//      return ("http://gutenberg.org/ebooks/" + id);
//    }
//  }

  //----------------------------------------------------------- from RDF ---

  private abstract class RDFReader extends DefaultHandler {

    static private final String PGTERMS_URI = "http://www.gutenberg.org/2009/pgterms/";
    static private final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    static private final String DCTERMS_URI = "http://purl.org/dc/terms/";

    public int count = 0;
    public String site = "";

    abstract protected String id2url (String id);


    private boolean inEbook = false;
    private boolean inTitle = false;
    private boolean inCreator = false;
    private boolean inAuthor = false;
    private boolean inLanguage = false;
    private boolean inLanguageValue = false;
    private boolean inCreated = false;
    private boolean inHasFormat = false;
    private boolean inModified = false;

    private StringBuffer title = new StringBuffer ();
    private StringBuffer author = new StringBuffer ();
    private StringBuffer language = new StringBuffer ();
    private StringBuffer created = new StringBuffer ();
    private StringBuffer modified = new StringBuffer ();
    private String id;


    public RDFReader (String site) {
      this.site = site;
    }

    @Override
    public void characters (char[] ch, int start, int length) throws SAXException {

      if (inLanguageValue) {
        language.append (ch, start, length); }
      else if (inCreated) {
        created.append (ch, start, length); }
      else if (inModified) {
        modified.append (ch, start, length); }
      else if (inTitle) {
        title.append (ch, start, length); }
      else if (inAuthor) {
        author.append (ch, start, length); }
    }

    @Override
    public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {

      if (PGTERMS_URI.equals (uri) && "ebook".equals (localName)) {
        inEbook = true;
        language.setLength (0);
        title.setLength (0);
        author.setLength (0);
        created.setLength (0);
        id = attributes.getValue (RDF_URI, "about").substring (7); }

      if (inEbook) {
        if (DCTERMS_URI.equals (uri) && "hasFormat".equals (localName)) {
          inHasFormat = true; }

        if (DCTERMS_URI.equals (uri) && "language".equals (localName)) {
          inLanguage = true; }

        if (DCTERMS_URI.equals (uri) && "title".equals (localName)) {
          inTitle = true; }

        if (DCTERMS_URI.equals (uri) && "creator".equals (localName)) {
          inCreator = true; }

        if (DCTERMS_URI.equals (uri) && "issued".equals (localName)) {
          inCreated = true; }}

      if (inLanguage) {
        if (RDF_URI.equals (uri) && "value".equals (localName)) {
          inLanguageValue = true; }}

      if (inCreator) {
        if (PGTERMS_URI.equals (uri) && "name".equals (localName)) {
          inAuthor = true;
          if (author.length () > 0) {
            author.append ("<br>"); }}}

      if (inHasFormat) {
        if (DCTERMS_URI.equals (uri) && "modified".equals (localName)) {
          inModified = true;
          modified.setLength (0); }}
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

      if (PGTERMS_URI.equals (uri) && "ebook".equals (localName)) {
        inEbook = false;
        if ("fr".equals (language.toString ())) {
          Date creationDate, modificationDate;
          try {
            creationDate = df.parse (created.toString ().substring (0, 10));
            modificationDate = df.parse (modified.toString ().substring (0, 10)); }
          catch (ParseException ex) {
            throw new SAXException (ex); }
          Entry e = new Entry (author.toString (), title.toString (),
                               creationDate, modificationDate,
                               id2url (id), site, id);
          entries.add (e);
          count++; }}

      if (inEbook) {
        if (DCTERMS_URI.equals (uri) && "hasFormat".equals (localName)) {
          inHasFormat = false; }

        if (DCTERMS_URI.equals (uri) && "language".equals (localName)) {
          inLanguage = false; }

        if (DCTERMS_URI.equals (uri) && "title".equals (localName)) {
          inTitle = false; }

        if (DCTERMS_URI.equals (uri) && "creator".equals (localName)) {
          inAuthor = false; }

        if (DCTERMS_URI.equals (uri) && "issued".equals (localName)) {
          inCreated = false; }}

      if (inLanguage) {
        if (RDF_URI.equals (uri) && "value".equals (localName)) {
          inLanguageValue = false; }}

      if (inCreator) {
        if (PGTERMS_URI.equals (uri) && "name".equals (localName)) {
          inAuthor = false; }}

      if (inHasFormat) {
        if (DCTERMS_URI.equals (uri) && "modified".equals (localName)) {
          inModified = false; }}
    }
  }

  private class GutenbergRDFReader extends RDFReader {
    public GutenbergRDFReader (String site) {
      super (site);
    }

    protected String id2url (String id) {
      return ("http://gutenberg.org/ebooks/" + id);
    }
  }

//  public int collectFromZippedRDF (URL url, OldRDFReader rdfReader) throws Exception {
//
//    InputStream s = url.openStream ();
//
//    try {
//      ZipInputStream zs = new ZipInputStream (s);
//
//      try {
//        zs.getNextEntry ();
//
//        SAXParserFactory spf = SAXParserFactory.newInstance ();
//        spf.setNamespaceAware (true);
//        spf.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, false);
//        spf.setValidating (false);
//
//        SAXParser sp = spf.newSAXParser ();
//        sp.parse (new InputSource (zs), rdfReader);
//
//        consoleOut.println (rdfReader.site + " : " + rdfReader.count);
//        return rdfReader.count; }
//
//      finally {
//        zs.close (); }}
//
//    finally {
//      s.close (); }
//  }


  public int collectFromZippedTaredRDFs (URI uri, RDFReader rdfReader) throws Exception {
    InputStream s = uri.toURL().openStream ();

    try {
      ZipInputStream zs = new ZipInputStream (s);

      try {
        zs.getNextEntry ();
        TarArchiveInputStream ts = new TarArchiveInputStream (zs);

        try {
          TarArchiveEntry te = null;

          while ((te = ts.getNextTarEntry ()) != null) {
            int toRead = (int) te.getSize ();
            byte[] content = new byte [toRead];
            int offset = 0;

            while (toRead > 0) {
              int nbRead = ts.read (content, offset, toRead);
              offset += nbRead;
              toRead -= nbRead; }

            SAXParserFactory spf = SAXParserFactory.newInstance ();
            spf.setNamespaceAware (true);
            spf.setFeature (XMLConstants.FEATURE_SECURE_PROCESSING, false);
            spf.setValidating (false);

            SAXParser sp = spf.newSAXParser ();
            sp.parse (new InputSource (new ByteArrayInputStream (content)), rdfReader); }

          consoleOut.println (rdfReader.site + " : " + rdfReader.count);
          return rdfReader.count; }

        finally {
          ts.close (); }}

      finally {
        zs.close (); }}

    finally {
      s.close (); }
  }
  //--------------------------------------------------------------- to JSON ---

  public void toJSON (File f, boolean includeDates) throws Exception {

    PrintStream out = new PrintStream (new FileOutputStream (f), true, "UTF-8");

    out.println ("{ \"aaData\": [");

    String prefix = "";
    for (Entry e : entries) {
      out.print (prefix);
      out.print (e.toJSON (includeDates));
      prefix = ",\n"; }

    out.println ("\n]}");

    out.close ();
  }

  public void toAtom (File f, Date since) throws Exception {

    PrintStream out = new PrintStream (new FileOutputStream (f), true, "UTF-8");

    out.println ("<?xml version='1.0' encoding='UTF-8'?>");
    out.println ("<feed xmlns='http://www.w3.org/2005/Atom' xml:lang='fr'>");
    out.println ("<title>noslivres.net</title>");
    out.println ("<updated>" + df.format (new Date ()) + "</updated>");

    out.println ("<author>");
    out.println ("<name>noslivres.net</name>");
    out.println ("<email>contact@noslivres.net</email>");
    out.println ("</author>");

    out.println ("<id>http://noslivres.net/uuid/60887a41-d799-11d9-b93C-0004939e7af6</id>");

    out.println ("<link href='http://noslivres.net'/>");
    out.println ("<link href='http://noslivres/feed-recents.xml' rel='self' type='application/atom+xml'/>");

    for (Entry e : entries) {
      String atom = e.toAtom (since);
      if (atom != null) {
        out.print (atom); }}

    out.println ("</feed>");

    out.close ();
  }

  //--------------------------------------------------------------- to SQL ---

  public void toSQL (File f) throws Exception {

    PrintStream out = new PrintStream (new FileOutputStream (f), true, "UTF-8");

    out.println ("SET SQL_MODE=\"NO_AUTO_VALUE_ON_ZERO\";");
    out.println ("SET time_zone = \"+00:00\";");

    out.println ("DROP TABLE IF EXISTS livres;");

    out.println ("CREATE TABLE `livres` (\n"
      + "  `titre`    varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + "  `auteur`   varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + "  `parution` varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + "  `maj`      varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + "  `site`     varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + "  `url`      varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + "  `mots`     varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci, \n"
      + " KEY `titre` (`titre`(333))"
      + ") ENGINE=MyISAM DEFAULT CHARSET=utf8;");


    for (Entry e : entries) {
      out.println (e.toSQL ()); }

    out.close ();
  }

  //------------------------------------------------------------ to CSV ---

  public void toCSV (File f) throws Exception {

    PrintStream out = new PrintStream (new FileOutputStream (f), true, "UTF-8");

    for (Entry e : entries) {
      out.println (e.toCSV ()); }

    out.close ();
  }

  //---------------------------------------------------------------------------

  public static void main (String[] args) throws Exception {

    consoleOut = new PrintStream (System.out, true, "UTF-8");

    try {
      boolean local = args.length > 0 && "-local".equals (args [0]);

      Catalog catalog = new Catalog ();

      catalog.collectFromCSV (makeURI ("http://noslivres.net/contributions/ixezede.txt", local),                             "ixezede");

      catalog.collectFromOPDS (makeURI ("http://meskach.free.fr/arbo/epub/katalog.xml", local),
                               catalog.new OPDSReader ("Meskach", "http://meskach.free.fr/arbo/epub"));

      catalog.collectFromCSV (makeURI ("https://docs.google.com/uc?id=1GLSni17FIKrXw5El36R_qfOcMK90Fedl&export=download", local),                  "TPBNB");
      catalog.collectFromCSV (makeURI ("http://beq.ebooksgratuits.com/BEQ_catalogue.txt", local),                            "BEQ");
      catalog.collectFromCSV (makeURI ("http://noslivres.net/contributions/bnr_liste_livre.txt", local),                     "BNR");
      catalog.collectFromCSV (makeURI ("http://efele.net/ebooks/efele_catalogue_commun.txt", local),                         "ÉFÉLÉ");

      catalog.collectFromOPDS (makeURI ("https://www.ebooksgratuits.com/opds/authors.php", local),
                               catalog.new OPDSReader ("ELG", "https://www.ebooksgratuits.com").followAllLinks());


      catalog.collectFromCSV (makeURI ("http://bibliotheque-russe-et-slave.com/liste.txt", local),                           "BRS");
      catalog.collectFromCSV (makeURI ("http://livres.gloubik.info/IMG/txt/ebooks_catalogue_commun.txt", local),             "Gloubik");
      catalog.collectFromCSV (makeURI ("https://www.rousseauonline.ch/rousseauonline.ch.txt", local),                        "rousseauonline");

      catalog.collectFromCSV (makeURI ("http://noslivres.net/contributions/catalogue-O'Monroy.txt", local),                  "Mobile Read - roger64");

      catalog.indirectCollectFromCSV (makeURI ("https://www.chineancienne.fr/recherche", local),
                                      "/app/download/[0-9]+/chineancienne_fr_liste_conso_[0-9]+.txt.t=[0-9]+",
                                      "Chine ancienne");

      /*
        catalog.collectFromCSV (makeURI ("http://eforge.eu/OPDS/Catalogue_libres.csv", true),                                "eForge");

        catalog.collectFromCSV (makeURI ("http://djelibeibi.unex.es/cgi-bin/list.py", local),                               "Djelibeibi");
        catalog.collectFromCSV (makeURI ("http://158.49.48.32/cgi-bin/list.py", local),                                     "Djelibeibi");
      */

//       catalog.collectFromOPDS (makeURI ("https://tools.wmflabs.org/wsexport/wikisource-fr-good.atom", local),
//                               catalog.new OPDSReader ("Wikisource", "https://fr.wikisource.org"));

       catalog.collectFromOPDS (makeURI ("https://ws-export.wmcloud.org/opds/fr/Bon_pour_export.xml", local),
                               catalog.new OPDSReader ("Wikisource", "https://fr.wikisource.org"));

//      catalog.collectFromZippedRDF (makeURI ("http://www.gutenberg.org/feeds/catalog.rdf.zip", local),
//                                    catalog.new GutenbergOldRDFReader ("Gutenberg"));

      catalog.collectFromZippedTaredRDFs (makeURI ("https://www.gutenberg.org/cache/epub/feeds/rdf-files.tar.zip", local),
                                          catalog.new GutenbergRDFReader ("Gutenberg"));

      //      catalog.collectFromOPDS (makeURI ("https://gallica.bnf.fr/opds?query=dc.format+adj+\"epub\"", local),
      //                               catalog.new OPDSReader ("Gallica", "https://gallica.bnf.fr"));

      catalog.collectFromCSV (makeURI ("http://noslivres.net/contributions/gallica.txt", local),                  "Gallica");


      consoleOut.println (catalog.entries.size () + " livres en tout");

      //  catalog.toJSON (new File ("books.json"), true);
      catalog.toCSV (new File ("www/books.csv"));

      Date recent = new Date (new Date ().getTime () - 31L*24*3600*1000);
      catalog.toAtom (new File ("www/feed-recents.xml"), recent);
      }

    catch (Exception e) {
      consoleOut.println (e.getMessage ());
      e.printStackTrace ();
    }
  }
}
