package org.rappsilber;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import rappsilber.config.AbstractRunConfig;
import rappsilber.config.DBConnectionConfig;
import rappsilber.config.RunConfig;
import rappsilber.db.ConnectionPool;
import rappsilber.ms.ToleranceUnit;
import rappsilber.ms.crosslinker.CrossLinker;
import rappsilber.ms.crosslinker.SymetricSingleAminoAcidRestrictedCrossLinker;
import rappsilber.ms.sequence.AminoAcid;
import rappsilber.ms.sequence.AminoLabel;
import rappsilber.ms.sequence.AminoModification;
import rappsilber.ms.sequence.Peptide;
import rappsilber.ms.sequence.Sequence;
import rappsilber.ms.sequence.ions.BLikeDoubleFragmentation;
import rappsilber.ms.sequence.ions.CrosslinkedFragment;
import rappsilber.ms.sequence.ions.Fragment;
import rappsilber.ms.sequence.ions.PeptideIon;
import rappsilber.ms.sequence.ions.loss.Loss;
import rappsilber.ms.spectra.Spectra;
import rappsilber.ms.spectra.SpectraPeak;
import rappsilber.ms.spectra.SpectraPeakCluster;
import rappsilber.ms.spectra.annotation.SpectraPeakAnnotation;
import rappsilber.ms.spectra.annotation.SpectraPeakMatchedFragment;
import rappsilber.ms.spectra.match.MatchedXlinkedPeptide;
import rappsilber.ms.spectra.match.MatchedXlinkedPeptideWeighted;
import rappsilber.utils.HashMapArrayList;
import rappsilber.utils.IntArrayList;
import rappsilber.utils.MyArrayUtils;
import rappsilber.utils.Util;
import rappsilber.utils.Version;
import rappsilber.utils.XiVersion;


/**
 * Root resource (exposed at "myresource" path)
 */
@Path("/annotate")
public class xiAnnotator {
    public class Cluster {
        int id;
        int charge;
        double mz;

        public Cluster(int id, int charge, double mz) {
            this.id = id;
            this.charge = charge;
            this.mz = mz;
        }
        
    }
    static ConnectionPool m_connections = null;
    static Connection m_connection = null;
    
    rappsilber.utils.Version version;

    public xiAnnotator() {
        try {
            final Properties properties = new Properties();
            try {
                properties.load(this.getClass().getResourceAsStream("project.properties"));
            } catch (Exception e) {
                properties.load(this.getClass().getClassLoader().getResourceAsStream("project.properties"));                
            }
            String[] v = properties.getProperty("annotator.version").split("\\.");
            
            version = new Version(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
            if (v.length > 3) {
                version.setExtension(v[3]);
            }
        } catch (IOException ex) {
            Logger.getLogger(xiAnnotator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
    
    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Path("/hello")
    @Produces( MediaType.APPLICATION_JSON )
    public String getHello() {
        return "{\"Hello World\"}";
    }
    
    private Connection getConnection() throws SQLException, FileNotFoundException, ParseException {
        
        if (m_connection != null && !m_connection.isClosed() ) {
            // make sure the connection is actually usable
            try {
                m_connection.createStatement().execute("SELECT 1+1");
            } catch (Exception e) {
                // seems something does not work with the current connection
                m_connections.free(m_connection);
                m_connection = null;
            }
        }
        
        if (m_connection == null || m_connection.isClosed()) {
            if (m_connection!=null  && m_connection.isClosed()) {
                m_connections.free(m_connection);
                m_connection=null;
            }
            if (m_connections == null) {
                String db_config = System.getProperty("XI_DB_CONFIG",null);
                String db_config_name = System.getProperty("XI_DB_CONF_NAME","xi3");
                
                DBConnectionConfig dbconf = new DBConnectionConfig();;
                if (db_config == null) 
                    dbconf.readConfig();
                else 
                    dbconf.readConfig(db_config);

                DBConnectionConfig.DBServer db = dbconf.getServers().get(0);
                String m_db_connection = db.connectionString;
                String m_db_user = db.user;
                String m_db_password = db.password;
                
                if (!db.name.contentEquals(db_config_name)) {
                    for (int i = 1; i<=dbconf.getServers().size(); i++) {
                        db = dbconf.getServers().get(i);
                        if (db.name.contentEquals(db_config_name)) {
                            m_db_connection = db.connectionString;
                            m_db_user = db.user;
                            m_db_password = db.password;
                            break;
                        }

                    }
                }
                
                //get the connection pool
                m_connections = new ConnectionPool("org.postgresql.Driver", m_db_connection, m_db_user, m_db_password);
            }
            m_connection = m_connections.getConnection();
        }
        return m_connection;
    }
    
    @GET
    @Path("/example")
    @Produces( MediaType.APPLICATION_XHTML_XML)
    public Response example(){
        return getResponse("<html xmlns='http://www.w3.org/1999/xhtml' xml:lang='en' lang='en'><head>\n" +
"<meta http-equiv='Content-Type' content='text/html; charset=UTF-8' />\n" +
"\n" +
"\n" +
"<script type='text/javascript'>\n" +
"var url = 'FULL';\n" +
"var http = getHTTPObject();\n" +
"function getHTTPObject() {\n" +
"	var http = false;\n" +
"	//Use IE's ActiveX items to load the file.\n" +
"	if(typeof ActiveXObject != 'undefined') {\n" +
"		try {http = new ActiveXObject(\"Msxml2.XMLHTTP\");}\n" +
"		catch (e) {\n" +
"			try {http = new ActiveXObject(\"Microsoft.XMLHTTP\");}\n" +
"			catch (E) {http = false;}\n" +
"		}\n" +
"	//If ActiveX is not available, use the XMLHttpRequest of Firefox/Mozilla etc. to load the document.\n" +
"	} else if (XMLHttpRequest) {\n" +
"		try {http = new XMLHttpRequest();}\n" +
"		catch (e) {http = false;}\n" +
"	}\n" +
"	return http;\n" +
"}\n\n" +
"function handler() {//Call a function when the state changes.\n" +
"	if(http.readyState == 4 && http.status == 200) {\n" +
"		document.getElementById('txtResponse').innerHTML = http.responseText;\n" +
"	}\n" +
"}\n" +
"\n" +
"function postMethod() {\n" +
"	var data=document.getElementById('txt').innerHTML;\n" +
"	http.open('POST', url, true);\n" +
"	\n" +
"	//Send the proper header infomation along with the request\n" +
"	http.setRequestHeader('Content-Type', 'application/json');\n" +
"\n" +
"	http.onreadystatechange = handler;\n" +
"	http.send(data);\n" +
"}\n" +
"\n" +
"</script>\n" +
"\n" +
"</head>\n" +
"\n" +
"<body>\n" +
"	<input type='button' value='annotate' onclick='postMethod()' />\n" +
"	<textarea id='txt' rows='15' cols='90'>\n" +
"		{'Peptides':[{'sequence':[{'aminoAcid':'K','Modification':''},{'aminoAcid':'Q','Modification':''},{'aminoAcid':'E','Modification':''},{'aminoAcid':'Q','Modification':''},{'aminoAcid':'E','Modification':''},{'aminoAcid':'S','Modification':''},{'aminoAcid':'L','Modification':''},{'aminoAcid':'G','Modification':''},{'aminoAcid':'S','Modification':''},{'aminoAcid':'N','Modification':''},{'aminoAcid':'S','Modification':''},{'aminoAcid':'K','Modification':''}]},{'sequence':[{'aminoAcid':'M','Modification':'ox'},{'aminoAcid':'N','Modification':''},{'aminoAcid':'A','Modification':''},{'aminoAcid':'N','Modification':''},{'aminoAcid':'K','Modification':''}]}],'LinkSite':[{'id':0,'peptideId':0,'linkSite':11},{'id':0,'peptideId':1,'linkSite':0}],'peaks':[{'mz':192.96811,'intensity':67.73983},{'mz':244.1642,'intensity':110.8582},{'mz':261.15775,'intensity':129.5965},{'mz':275.59494,'intensity':80.46323},{'mz':314.6499,'intensity':73.12415},{'mz':318.53464,'intensity':74.77551},{'mz':332.19034,'intensity':129.8459},{'mz':352.76807,'intensity':80.8383},{'mz':368.19485,'intensity':161.2779},{'mz':369.15225,'intensity':70.75565},{'mz':391.24362,'intensity':87.39551},{'mz':407.71011,'intensity':765.3865},{'mz':408.21057,'intensity':168.6249},{'mz':429.08914,'intensity':1021.067},{'mz':439.22614,'intensity':1080.865},{'mz':439.72787,'intensity':146.117},{'mz':446.2355,'intensity':1156.349},{'mz':447.24475,'intensity':107.6111},{'mz':473.73792,'intensity':582.5543},{'mz':474.23178,'intensity':151.0379},{'mz':479.22253,'intensity':89.13316},{'mz':482.74237,'intensity':1837.839},{'mz':483.24542,'intensity':517.5132},{'mz':485.22946,'intensity':160.8755},{'mz':494.23871,'intensity':117.7743},{'mz':504.73248,'intensity':104.7452},{'mz':514.25714,'intensity':168.123},{'mz':519.32617,'intensity':84.28606},{'mz':530.75781,'intensity':348.261},{'mz':531.25165,'intensity':574.6472},{'mz':531.75232,'intensity':195.0436},{'mz':532.28021,'intensity':122.7291},{'mz':539.76459,'intensity':461.346},{'mz':540.26691,'intensity':216.6365},{'mz':542.25702,'intensity':383.6699},{'mz':542.74811,'intensity':144.7962},{'mz':551.26862,'intensity':138.4185},{'mz':556.75995,'intensity':131.5791},{'mz':557.25281,'intensity':154.4313},{'mz':565.75897,'intensity':124.528},{'mz':566.2757,'intensity':105.5735},{'mz':567.77228,'intensity':99.65214},{'mz':574.27252,'intensity':177.6217},{'mz':574.77173,'intensity':304.3945},{'mz':576.76465,'intensity':337.0186},{'mz':577.26617,'intensity':151.8026},{'mz':583.28101,'intensity':403.2102},{'mz':583.77972,'intensity':189.2003},{'mz':584.76569,'intensity':107.6447},{'mz':585.76959,'intensity':493.1025},{'mz':586.26678,'intensity':473.7382},{'mz':588.81873,'intensity':233.3332},{'mz':593.77887,'intensity':196.0799},{'mz':594.27661,'intensity':474.5169},{'mz':594.7774,'intensity':2625.148},{'mz':595.19702,'intensity':98.07767},{'mz':595.28461,'intensity':770.8936},{'mz':600.28125,'intensity':93.24009},{'mz':602.78687,'intensity':539.2776},{'mz':603.27844,'intensity':1425.828},{'mz':603.77917,'intensity':411.5556},{'mz':611.79083,'intensity':1100.022},{'mz':612.29089,'intensity':786.955},{'mz':614.30945,'intensity':189.8356},{'mz':620.92456,'intensity':94.50323},{'mz':622.67065,'intensity':211.5718},{'mz':623.00433,'intensity':308.2873},{'mz':623.32965,'intensity':160.2828},{'mz':633.36646,'intensity':106.8811},{'mz':643.30194,'intensity':564.7256},{'mz':643.97461,'intensity':92.41217},{'mz':644.30664,'intensity':106.7596},{'mz':645.85065,'intensity':86.88284},{'mz':649.98712,'intensity':91.16106},{'mz':650.3183,'intensity':272.1689},{'mz':651.32245,'intensity':139.994},{'mz':655.65698,'intensity':146.5744},{'mz':658.32581,'intensity':139.126},{'mz':659.32867,'intensity':227.4465},{'mz':659.82428,'intensity':339.1299},{'mz':661.66272,'intensity':355.9604},{'mz':661.99719,'intensity':261.6057},{'mz':662.33771,'intensity':205.5486},{'mz':703.34155,'intensity':200.9261},{'mz':711.84381,'intensity':425.8564},{'mz':712.34216,'intensity':443.5017},{'mz':730.33459,'intensity':528.4755},{'mz':731.33783,'intensity':306.635},{'mz':734.41376,'intensity':187.7748},{'mz':767.39771,'intensity':118.5136},{'mz':767.85651,'intensity':156.7686},{'mz':776.3606,'intensity':152.4337},{'mz':776.87164,'intensity':151.6859},{'mz':792.875,'intensity':214.166},{'mz':793.36304,'intensity':110.3984},{'mz':801.38068,'intensity':355.2381},{'mz':801.87268,'intensity':427.9933},{'mz':802.37146,'intensity':169.9695},{'mz':810.38464,'intensity':701.1418},{'mz':810.88306,'intensity':447.7175},{'mz':825.41553,'intensity':142.4287},{'mz':835.48615,'intensity':151.6225},{'mz':840.39972,'intensity':185.6205},{'mz':843.41937,'intensity':2103.097},{'mz':844.42188,'intensity':598.3317},{'mz':847.95972,'intensity':203.4357},{'mz':848.46552,'intensity':218.2574},{'mz':867.39832,'intensity':150.6456},{'mz':876.97156,'intensity':152.0691},{'mz':877.44507,'intensity':2751.281},{'mz':878.44775,'intensity':713.0289},{'mz':893.90674,'intensity':138.2526},{'mz':894.41077,'intensity':196.3892},{'mz':900.44226,'intensity':714.466},{'mz':901.44568,'intensity':319.9002},{'mz':902.92096,'intensity':264.5187},{'mz':903.41901,'intensity':359.6289},{'mz':904.91998,'intensity':188.0056},{'mz':924.98889,'intensity':201.2831},{'mz':933.49634,'intensity':699.1588},{'mz':934.00317,'intensity':596.2888},{'mz':934.4953,'intensity':325.0743},{'mz':942.42786,'intensity':214.3432},{'mz':949.50665,'intensity':147.7699},{'mz':951.43414,'intensity':258.3941},{'mz':951.92719,'intensity':259.7183},{'mz':959.93719,'intensity':328.4117},{'mz':960.44067,'intensity':498.7733},{'mz':964.47498,'intensity':1128.607},{'mz':965.47015,'intensity':294.6219},{'mz':969.44635,'intensity':127.3267},{'mz':987.47229,'intensity':771.5002},{'mz':988.47455,'intensity':223.6013},{'mz':1004.4575,'intensity':103.0786},{'mz':1061.5206,'intensity':167.1585},{'mz':1078.5142,'intensity':103.0195},{'mz':1101.5148,'intensity':627.6382},{'mz':1102.5326,'intensity':281.1112},{'mz':1116.5856,'intensity':155.2629},{'mz':1134.1617,'intensity':74.63107},{'mz':1134.5724,'intensity':287.1478},{'mz':1170.5297,'intensity':155.2897},{'mz':1187.5413,'intensity':174.4809},{'mz':1188.5481,'intensity':456.1581},{'mz':1205.5789,'intensity':107.7451},{'mz':1536.1685,'intensity':73.7821},{'mz':1563.991,'intensity':85.92266},{'mz':1625.6926,'intensity':75.41674},{'mz':1625.9557,'intensity':76.29048},{'mz':1833.2148,'intensity':84.97263},{'mz':1916.1755,'intensity':82.42414}],'annotation':{'fragmentTolerance':{'tolerance':20,'unit':'ppm'},'modifications':[{'aminoacid':'M','id':'ox','mass':147.03539963}],'ions':[{'type':'PeptideIon'},{'type':'BIon'},{'type':'YIon'}],'cross-linker':{'modMass':138.06807961},'precursorCharge':3}}\n" +
"	</textarea>\n" +
"	<textarea id='txtResponse' rows='15' cols='90'>\n" +
"	</textarea>\n" +
"\n" +
"</body>\n" +
"</html>", MediaType.TEXT_HTML_TYPE);
    }
    
    @POST
    @Path("/FULL")
    @Consumes(MediaType.APPLICATION_JSON ) 
    @Produces(MediaType.APPLICATION_JSON ) 
    public Response getFullAnnotation(String msg) throws ParseException {
        //setup the config
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /FULL {0}", msg);
        StringBuilder sb = new StringBuilder();
        try {
            Gson gson = new Gson();
            final LinkedTreeMap result = gson.fromJson(msg , LinkedTreeMap.class);        
            final LinkedTreeMap annotation = ((LinkedTreeMap)result.get("annotation"));
            final ArrayList<LinkedTreeMap> mods = (ArrayList<LinkedTreeMap>) annotation.get("modifications");
            final LinkedTreeMap fragmentTolerance = (LinkedTreeMap) annotation.get("fragmentTolerance");
            final LinkedTreeMap precoursorTolerance = (LinkedTreeMap) annotation.get("precoursorTolerance");
            final Object customConfig = annotation.get("custom");
            
            AbstractRunConfig config = new AbstractRunConfig() {
                    {
//                        evaluateConfigLine("modification:known::SYMBOLEXT:ox;MODIFIED:X;DELTAMASS:15.99491463");
//                        evaluateConfigLine("modification:known::SYMBOLEXT:cm;MODIFIED:C,K,H,D,E,S,T,Y;DELTAMASS:15.99491463");
                        // modifications
                        if (mods != null) {
                            for (LinkedTreeMap modTM : mods) {

                                if (modTM.get("aminoAcids") != null && modTM.get("aminoAcids") instanceof ArrayList) {
                                    ArrayList<String> allAAs=(ArrayList<String>) modTM.get("aminoAcids");
                                    HashSet<String> mods = new HashSet<>();
                                    for (String aa : allAAs) {
                                        if (aa.contentEquals("X") || aa.contentEquals("*")) {
                                            for (AminoAcid a : getAllAminoAcids().toArray(new AminoAcid[0])) 
                                            if (!mods.contains(a.SequenceID) && ! (a instanceof AminoModification)) {
                                                mods.add(a.SequenceID);
                                                addModification(a.SequenceID, modTM);
                                            }
                                        } else if (!mods.contains(aa)) {
                                            mods.add(aa);
                                            addModification(aa, modTM);
                                        }

                                    }
                                } else {
                                    String saa = modTM.get("aminoacid").toString();
                                    AminoAcid a =this.getAminoAcid(saa);
                                    AminoModification am = new AminoModification(modTM.get("aminoacid").toString() + modTM.get("id").toString() , 
                                            a, Double.parseDouble(modTM.get("mass").toString()));
                                    addVariableModification(am);
                                }
                            }
                        }
                        //  tolerance
                        if (precoursorTolerance == null) {
                            this.setPrecoursorTolerance(new ToleranceUnit(1, "Da"));
                        } else {
                            this.setPrecoursorTolerance(new ToleranceUnit(precoursorTolerance.get("tolerance").toString(), precoursorTolerance.get("unit").toString()));
                        }
                        
                        this.setFragmentTolerance(new ToleranceUnit(fragmentTolerance.get("tolerance").toString(), fragmentTolerance.get("unit").toString()));
                        for (LinkedTreeMap ion : (ArrayList<LinkedTreeMap>) annotation.get("ions")) {
                            this.evaluateConfigLine("fragment:"+ion.get("type").toString());
                        }
                        
                        LinkedTreeMap xl = (LinkedTreeMap) annotation.get("cross-linker");
                        double xlMas = Double.valueOf(xl.get("modMass").toString());
                        this.addCrossLinker(new SymetricSingleAminoAcidRestrictedCrossLinker("XL", xlMas, xlMas, new AminoAcid[]{}));
                        
                        
                        evaluateConfigLine("loss:AminoAcidRestrictedLoss:NAME:CH3SOH;aminoacids:Mox;MASS:63.99828547");
                        evaluateConfigLine("loss:AminoAcidRestrictedLoss:NAME:H20;aminoacids:S,T,D,E;MASS:18.01056027;cterm");
                        evaluateConfigLine("loss:AminoAcidRestrictedLoss:NAME:NH3;aminoacids:R,K,N,Q;MASS:17.02654493;nterm");
                        
                        if (customConfig instanceof ArrayList) {
                            for (Object o : (ArrayList) customConfig) {
                                evaluateConfigLine(o.toString());
                            }
                        } else if (customConfig instanceof String) {
                            evaluateConfigLine(customConfig.toString());
                        }
                    }

                private AminoModification addModification(String aa, LinkedTreeMap modTM) throws NumberFormatException {
                    AminoAcid a =this.getAminoAcid(aa);
                    AminoModification am = new AminoModification(a.SequenceID + modTM.get("id").toString() ,
                            a,
                            a.mass+Double.parseDouble(modTM.get("mass").toString()));
                    addVariableModification(am);
                    return am;
                }
            };
            
            // cretae the spectrum
            Spectra spectrum = new Spectra();
            for (LinkedTreeMap peak : (ArrayList<LinkedTreeMap>)result.get("peaks")) {
                spectrum.addPeak(Double.valueOf(peak.get("mz").toString()), Double.valueOf(peak.get("intensity").toString()));
            }
            spectrum.setPrecurserCharge(((Double)annotation.get("precursorCharge")).intValue());
            Double precMZ =  (Double)annotation.get("precursorMZ");
            if (precMZ != null) {
                spectrum.setPrecurserMZ(precMZ);
            }
            Double precIntensity =  (Double)annotation.get("precursorIntensity");
            if (precIntensity != null) {
                spectrum.setPrecurserIntensity(precIntensity);
            }
            
            ArrayList<LinkedTreeMap> jsonpeps = (ArrayList<LinkedTreeMap>) result.get("Peptides");
            Peptide[] peps = new Peptide[jsonpeps.size()];
            // get the peptides
            for (int p =0 ; p< peps.length; p++) {
                // get the residues
                ArrayList<LinkedTreeMap> seq = (ArrayList<LinkedTreeMap>) jsonpeps.get(p).get("sequence");
                AminoAcid[] pepArray  = new  AminoAcid[seq.size()];
                for (int aa = 0; aa<pepArray.length;aa ++) {
                    LinkedTreeMap jsonAA = seq.get(aa);
                    String id=jsonAA.get("aminoAcid").toString()+jsonAA.get("Modification").toString();
                    pepArray[aa]=config.getAminoAcid(id);
                }
                peps[p] = new Peptide(new Sequence(pepArray), 0, pepArray.length);
            }
            ArrayList<Integer> links =new ArrayList<>();
            if (peps.length > 1) {
                for (int i=0;i<peps.length;i++) {
                    links.add(0);
                }
                for (LinkedTreeMap link : (ArrayList<LinkedTreeMap>) result.get("LinkSite")) {
                    links.set(Double.valueOf(link.get("peptideId").toString()).intValue(), Double.valueOf(link.get("linkSite").toString()).intValue());
                }
            }

            sb = getJSON(spectrum, config, peps, links, 0, null, null);
        } catch (Exception e) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING,"Exception from request",e);
            return getResponse(exception2String(e),MediaType.TEXT_PLAIN_TYPE);
        }
        return getResponse(sb.toString(), MediaType.APPLICATION_JSON_TYPE);
        //return getResponse(sb.toString().replaceAll("[\\t\\s]*[\\n\\r][\\t\\s]*", ""), MediaType.APPLICATION_JSON_TYPE);
    }

    @GET
    @Path("/knownModifications")
    @Produces(MediaType.APPLICATION_JSON ) 
    public Response getKnownModifications() throws ParseException {
        //setup the config
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /knownModifications");
        StringBuilder sb = new StringBuilder();
        try {
            Connection con = getConnection();
            
            final ResultSet rs = con.createStatement().executeQuery("select 'modification:variable:'|| description from modification");
            
            AbstractRunConfig config = new AbstractRunConfig() {
                    {
                        // modifications
                        while (rs.next()) {
                            evaluateConfigLine(rs.getString(1));
                        }

                        
                    }
            };
            HashMap<String,HashMap<Double,ArrayList<AminoModification>>> allMods = new HashMap<>();
            
            
            for (AminoModification am : config.getVariableModifications()) {
                String id = am.SequenceID.substring(1);
                double diff = Math.round(am.weightDiff *10000.0);
                AminoAcid aa = am.BaseAminoAcid;
                while (aa instanceof AminoModification) {
                    diff +=  Math.round(((AminoModification) aa).weightDiff*10000.0);
                    aa = ((AminoModification) aa).BaseAminoAcid;
                }
                diff /= 10000.0;
                HashMap<Double,ArrayList<AminoModification>> idMods = allMods.get(id);
                if (idMods == null) {
                    idMods = new HashMap<>();
                    allMods.put(id, idMods);
                }
                ArrayList<AminoModification> mods = idMods.get(diff);
                if (mods == null) {
                    mods = new ArrayList<>();
                    idMods.put(diff, mods);
                }
                mods.add(am);
            }
            
            
            sb.append("{\"modifications\":[\n");
            for (Map.Entry<String,HashMap<Double,ArrayList<AminoModification>>> idMods : allMods.entrySet()) {
                
                for (Map.Entry<Double,ArrayList<AminoModification>> md : idMods.getValue().entrySet()) {
                    HashSet<AminoAcid> aas = new HashSet<>();
                    sb.append("\t{\"id\":\"").append(idMods.getKey()).append("\",\"mass\":").append(md.getKey()).append(",\"aminoAcids\":[");
                    for (AminoModification am : md.getValue()) {
                        AminoAcid aa = am.BaseAminoAcid;
                        while (aa instanceof AminoModification) {
                            aa = ((AminoModification) aa).BaseAminoAcid;
                        }
                        if (!aas.contains(aa)) {
                            sb.append("\"").append(aa.SequenceID).append("\",");
                            aas.add(aa);
                        }
                    }
                    sb.deleteCharAt(sb.length()-1);
                    sb.append("]},\n");
                }
            }
            sb.deleteCharAt(sb.length()-1);
            sb.deleteCharAt(sb.length()-1);
            sb.append("\n]}");
//            
//            for (AminoModification am : config.getVariableModifications()) {
//                if (!am.SequenceID.startsWith("X")) {
//                    sb.append("{\"aminoacid\":\"").append(am.BaseAminoAcid.SequenceID.substring(0, 1)).append("\",");
//                    sb.append("\"id\":\"").append(am.SequenceID.substring(1)).append("\",");
//                    sb.append("\"massDiff\":").append(am.mass).append("},");
//                }
//            }
//            sb.append("]}");
        } catch (Exception e) {
            return getResponse(exception2String(e),MediaType.TEXT_PLAIN_TYPE);
        }
        return getResponse(sb.toString(), MediaType.APPLICATION_JSON_TYPE);
        //return getResponse(sb.toString().replaceAll("[\\t\\s]*[\\n\\r][\\t\\s]*", ""), MediaType.APPLICATION_JSON_TYPE);
    }
    
    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Path("/{searchID}/{searchRID}/{matchID}")
    @Produces( MediaType.APPLICATION_JSON )
    public Response getAnnotation(@PathParam("searchID") Integer searchID, @PathParam("searchRID") String searchRID, @PathParam("matchID") long matchID, @QueryParam("peptide") List<String> Peptides, @QueryParam("link") List<Integer> links, @QueryParam("custom") List<String> custom, @DefaultValue("1") @QueryParam("firstresidue") int firstResidue, @DefaultValue("true") @QueryParam("prettyprint") boolean prettyprint) {
        StringBuilder sb = new StringBuilder("");
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2}", new Object[]{searchID, searchRID, matchID});

        try {
            //get the connection pool
            Connection con = getConnection();
            // read the config
            AnnotatorConfig config = new AnnotatorConfig(con);
            config.readConfig(searchID);
            for (String conf : custom) {
                config.addCustomConfig(conf);
            }
            config.addCustomConfig("FRAGMENTTREE:default");
            config.storeObject("FRAGMENTTREE", "default");

            // make sure the random id fits
//            if (!config.getRandomIDs()[0].contentEquals(searchRID)) {
//                return getResponse("{\"error\":\"Search not found\"}", MediaType.APPLICATION_JSON_TYPE);
//            }
            // turn peptides from string into a peptide replresentation with xlink
            
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - read spectrum", new Object[]{searchID, searchRID, matchID});
            // get the spectrum
            String spec = "select s.*, sm.precursor_charge as match_charge from spectrum_match sm inner join spectrum s on sm.id = " + matchID + " and sm.spectrum_id = s.id and sm.search_id = " + searchID;
            String peaks = "select s.* from spectrum_match sm inner join spectrum_peak s on sm.id = " + matchID + " and sm.spectrum_id = s.spectrum_id  and sm.search_id = " + searchID +" ORDER BY mz";
            Statement s = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ResultSet rsPeaks = s.executeQuery(peaks);
            ArrayList<SpectraPeak> aPeaks = new ArrayList<SpectraPeak>();
            // read in all peaks
            while (rsPeaks.next()) {
                aPeaks.add(new SpectraPeak(rsPeaks.getDouble("mz"), rsPeaks.getDouble("intensity")));
            }
            rsPeaks.close();

            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - read spectrum", new Object[]{searchID, searchRID, matchID});
            
            Spectra spectrum ;
            ResultSet rsSpec = s.executeQuery(spec);
            if (rsSpec.next()) {
                spectrum = new Spectra(-1,rsSpec.getDouble("precursor_intensity"),rsSpec.getDouble("precursor_mz"),rsSpec.getInt("match_charge"),aPeaks);
            } else {
                
//                m_connection_pool.free(con);
                //return Response."No Spectra";
                return getResponse("{\"error\":\"No Spectra\"}", MediaType.APPLICATION_JSON_TYPE);
            }
            Double expCharge = rsSpec.getDouble("precursor_charge");
            rsSpec.close();

            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - read db peptides", new Object[]{searchID, searchRID, matchID});
            String pepSQL = "SELECT "
                    + " p.sequence, p.peptide_length, p.mass, "
                    + " mp.match_type, "
                    + " mp.link_position, "
                    + " hp.peptide_position, "
                    + " protein_length, "
                    + " pr.sequence "
                    + " FROM matched_peptide mp INNER JOIN peptide p ON mp.match_id = " + matchID + " AND mp.search_id = " + searchID + " AND mp.peptide_ID = p.ID "
                    + " INNER JOIN has_protein hp ON p.id = hp.peptide_id "
                    + " INNER JOIN protein pr ON hp.protein_id = pr.id;";
            
            ArrayList<Boolean> isNterm = new ArrayList<>(2);
            ArrayList<Boolean> isCterm = new ArrayList<>(2);
            ArrayList<String>  sequence = new ArrayList<>(2);
            ArrayList<Integer>  length = new ArrayList<>(2);
            ArrayList<Peptide>  dbPeptide = new ArrayList<>(2);

            ResultSet rsPep = s.executeQuery(pepSQL);
            while (rsPep.next()) {
                int p = rsPep.getInt(4)-1;
                if (p>=sequence.size())  {
                    for (int i=sequence.size();i<=p;i++) {
                        isNterm.add(null) ;
                        isCterm.add(null) ;
                        sequence.add(null) ;
                        length.add(null) ;
                        dbPeptide.add(null) ;
                    }
                }
                String pepSeq = rsPep.getString(1);
                String protSeq = rsPep.getString(8);
                int pepLen = rsPep.getInt(2);
                length.set(p,pepLen);
                int protLen = rsPep.getInt(7);
                int pos = rsPep.getInt(6);
                if (pos == 0 || (pos == 1 && protSeq.startsWith("M"))) {
                    isNterm.set(p, true);
                }
                
                if (pos + pepLen ==  protLen) {
                    isCterm.set(p, true);
                }
                
                sequence.set(p, pepSeq);
                
            }
            
            for (int p = 0;p<sequence.size();p++) {
                final Boolean nt = isNterm.get(p);
                final Boolean ct = isCterm.get(p);
                dbPeptide.set(p,new Peptide(new Sequence(sequence.get(p),config), 0, length.get(p)) {
                    @Override
                    public boolean isNTerminal() {
                        return nt != null;
                    }

                    @Override
                    public boolean isCTerminal() {
                        return ct != null;
                    }
                    
                });
            }

            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - generate request peptides", new Object[]{searchID, searchRID, matchID});
            Peptide[] peps = null;
            if (Peptides.size() == 0) {
                peps = new Peptide[dbPeptide.size()];
                peps = dbPeptide.toArray(peps);
            } else {
                peps = new Peptide[Peptides.size()];
                for (int p=0; p<Peptides.size();p++) {
                    Sequence seq = new Sequence(Peptides.get(p), config);
                    Peptide aPep = new Peptide(seq, 0, seq.length());
                    for (final Peptide dbp : dbPeptide) {
                        if (aPep.toStringBaseSequence().contentEquals(dbp.toStringBaseSequence())) {
                             
                            aPep = new Peptide(aPep) {
                                
                                @Override
                                public boolean isNTerminal() {
                                    return dbp.isNTerminal();
                                }

                                @Override
                                public boolean isCTerminal() {
                                    return dbp.isCTerminal();
                                }
                                
                            };
                            break ;
                        }
                    }
                    peps[p] = aPep;
                    
                }
            }
            
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - generate json", new Object[]{searchID, searchRID, matchID});
            sb = getJSON(spectrum, config, peps, links, firstResidue, expCharge.intValue(), (long) matchID);
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - done with json", new Object[]{searchID, searchRID, matchID});

        } catch (Exception e) {
    //        return Response.ok("{\"error\":\""+exception2String(e)+"\"}", MediaType.APPLICATION_JSON).header("Access-Control-Allow-Origin", "*").build();
            System.err.println("error" + e);
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "REQUEST /{0}/{1}/{2} - error", new Object[]{searchID, searchRID, matchID});
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "eroror is :\n", e);
            return getResponse("{\"error\":\""+exception2String(e)+"\"}", MediaType.APPLICATION_JSON_TYPE);
//            return exception2String(e);
//            StringBuilder sbError = new StringBuilder();
//            for (StackTraceElement ste :e.getStackTrace()) {
//                sbError.append(ste.toString() +"\n");
//            }
//            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error", e);
//            return "no connection to database \n" +e.getMessage() +"\n"+ sbError;
        }
        
//        Logger.getLogger(this.getClass().getName()).log(Level.INFO, sb.toString());
        if (prettyprint)
            return getResponse(sb.toString(), MediaType.APPLICATION_JSON_TYPE);
        
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "REQUEST /{0}/{1}/{2} - return json", new Object[]{searchID, searchRID, matchID});
        return getResponse(sb.toString().replaceAll("[\n\t]*", ""), MediaType.APPLICATION_JSON_TYPE);
    }

    protected StringBuilder getJSON(Spectra spectrum, RunConfig config, Peptide[] peps, List<Integer> links, int firstResidue, Integer expCharge, Long psmID) {
        ArrayList<Cluster> cluster = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        spectrum.setTolearance(config.getFragmentTolerance());
        try {
            if (!config.isLowResolution())
                config.getIsotopAnnotation().anotate(spectrum);
        } catch (Exception e) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error anotating Isotop clusters",e);
            System.err.println("Error anotating Isotop clusters" + e);
        }
        MatchedXlinkedPeptide match = null;
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "get match");
        match = getMatch(spectrum, peps, links, config,firstResidue);
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "add peptide to json");
        addPeptides(sb, peps);
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "add links to json");
        addLinks(sb, peps, match);
        sb.append("\n\"peaks\" :[\n\t");
        StringBuilder sbCluster = new StringBuilder("\n\"clusters\": [ ");
        HashMapArrayList<Fragment,Integer> fragmentCluster = new HashMapArrayList<Fragment,Integer>();
        HashMap<SpectraPeak,Integer> peak2ID = new HashMap<>();
        HashMapArrayList<SpectraPeak,Integer> peaksCluster = new HashMapArrayList<SpectraPeak,Integer>();
        HashMapArrayList<Fragment,SpectraPeakMatchedFragment> framentMatches = new HashMapArrayList<>();

        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "extract cluster");
        int cID=extractCluster(spectrum, peak2ID, sbCluster,framentMatches,  fragmentCluster, peaksCluster,cluster);

        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "add peaks to json");
        addPeaks(spectrum, peaksCluster, sbCluster, cID, framentMatches, fragmentCluster, sb,cluster);
        // add the clusters
        sbCluster.deleteCharAt(sbCluster.length()-1);
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "add cluster to json");
        sb.append(sbCluster);
        sb.append("],");
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "add fragments to json");
        addFragments(sb, fragmentCluster, match, peps,cluster,framentMatches,config);
        Logger.getLogger(this.getClass().getName()).log(Level.FINE, "add metadata to json");
        appendMetaData(sb, config, peps,match, expCharge,psmID);
        
        sb.append("\n}");
//            m_connection_pool.free(con);
        return sb;
    }
    
    
    protected Response getResponse(String message, MediaType mt) {
        return Response.ok(message, mt)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET")
                .header("Access-Control-Allow-Headers", "Content-Type, Accept")
                .build();
    }

    protected void appendMetaData(StringBuilder sb, RunConfig config, Peptide[] peps, MatchedXlinkedPeptide match, Integer expCharge, Long psmID) {
        sb.append(",\n\"annotation\":{\n\t\"xiVersion\":\"").append(XiVersion.getVersionString())
                .append("\",\n\t\"annotatorVersion\":\"").append(version.toString())
                .append("\",\n\t\"fragementTolerance\":\"").append(config.getFragmentTolerance().toString()).append("\"");
        boolean hasmod = false;
        HashSet<AminoAcid> mods =new HashSet<>();
        StringBuilder sbMods= new StringBuilder();
        for (Peptide p : peps) {
            for (AminoAcid aa : p) {
                if (aa instanceof AminoModification) {
                    AminoModification am = (AminoModification) aa;
                    if (!mods.contains(am)) {
                        mods.add(am);
                        sbMods.append("\n\t\t").append(modificationToString(am));
                    }
                } else if (aa instanceof AminoLabel) {
                    AminoLabel al = (AminoLabel) aa;
                    if (!mods.contains(al)) {
                        mods.add(al);
                        sbMods.append("\n\t\t").append(labelToString(al));
                    }
                }
            }
        }
        if (!mods.isEmpty()) {
            sbMods.deleteCharAt(sbMods.length()-1);
            sb.append(",\n\t\"modifications\":[").append(sbMods).append("\n\t]");
        }
        StringBuilder sbIon= new StringBuilder();
        for (Method m: config.getFragmentMethods()) {
            if (!(m.getDeclaringClass().isAssignableFrom(Loss.class))) {
                sbIon.append("\n\t\t{\"type\":\"").append(m.getDeclaringClass().getSimpleName()).append("\"},");
            }
        }
        if (sbIon.length() != 0) {
            sbIon.deleteCharAt(sbIon.length()-1);
            sb.append(",\n\t\"ions\":[").append(sbIon).append("\n\t],");
        }
        Double xlmas = 0d;
        if (match.getCrosslinker() != null) {
            xlmas = match.getCrosslinker().getCrossLinkedMass();
        }
        String sXLMass = xlmas.toString();
        if (xlmas == Double.POSITIVE_INFINITY)
            xlmas = Double.MAX_VALUE;
        if (xlmas == Double.NEGATIVE_INFINITY)
            xlmas = -Double.MAX_VALUE;
        
        sb.append("\n\t\"cross-linker\":{\"modMass\":"+(Double.isNaN(xlmas) ? "null" : xlmas)+"},");
        sb.append("\n\t\"precursorCharge\": "+match.getSpectrum().getPrecurserCharge() +",");
        sb.append("\n\t\"precursorIntensity\": "+match.getSpectrum().getPrecurserIntensity()+",");
        sb.append("\n\t\"precursorMZ\": "+match.getSpectrum().getPrecurserMZ()+",");
        if (expCharge != null ) 
            sb.append("\n\t\"experimentalCharge\": "+expCharge+",");
        if (psmID != null ) 
            sb.append("\n\t\"psmID\": "+psmID+",");
        if (match.getSpectrum().getPrecurserMZ() == -1) {
            sb.append("\n\t\"precursorError\": \"\"");
        } else {
            sb.append("\n\t\"precursorError\": \"" + config.getPrecousorTolerance().toString(match.getSpectrum().getPrecurserMass(), match.getCalcMass())+"\"");
        }
        sb.append("\n}");
    }
    
    public String modificationToString(AminoModification mod) {
        AminoAcid b = mod.BaseAminoAcid;
        String modID = mod.SequenceID;
        if (mod.SequenceID.startsWith(b.SequenceID)) {
            modID=mod.SequenceID.substring(b.SequenceID.length());
        }
        return "{\"aminoacid\":\"" + mod.BaseAminoAcid + "\", \"id\":\""+modID + "\", \"mass\":" + mod.mass + ", \"massDifference\":"+(mod.mass-b.mass)+"},";
    }

    public String labelToString(AminoLabel mod) {
        AminoAcid b = mod.BaseAminoAcid;
        String modID = mod.SequenceID;
        if (mod.SequenceID.startsWith(b.SequenceID)) {
            modID=mod.SequenceID.substring(b.SequenceID.length());
        }
        
        return "{\"aminoacid\":\"" + mod.BaseAminoAcid + "\", \"id\":\""+modID + "\", \"mass\":" + mod.mass + ", \"massDifference\":"+(mod.mass-b.mass)+"},";
    }
    
    protected void addFragments(StringBuilder sb, HashMapArrayList<Fragment, Integer> fragmentCluster, MatchedXlinkedPeptide match, Peptide[] peps, ArrayList<Cluster> clusters, HashMapArrayList<Fragment,SpectraPeakMatchedFragment> framentMatches, RunConfig conf) {
        // add the fragments
        sb.append("\n\"fragments\": [ ");
        int fid =-1;
        
        for(Map.Entry<Fragment,ArrayList<Integer>> e : fragmentCluster.entrySet()) {
            Fragment f = e.getKey();
            Peptide p = f.getPeptide();
            int pid = 0;
            if (p==match.getPeptide2()) {
                pid =1;
            }
            ArrayList<Integer> cluster = e.getValue();
            
            Fragment baseFrag = f;
            String type="";
            if (f.isClass(Loss.class)) {
                baseFrag=((Loss)f).getBaseFragment();
            }
            StringBuilder sbType = new StringBuilder();
            FragmentType(f, sbType);
            String[] ToolTips = new String[cluster.size()];
            StringBuilder clusterInfos= fragmentClusterInfos(cluster, clusters, framentMatches, f, conf,"\n\t\t\t");

            sb.append("\n\t{\n\t\t\"name\": \"").append(f.name())
                    .append("\", \n\t\t\"peptideId\":").append(pid)
                    .append(",\n\t\t\"type\":\"").append(sbType)
                    .append("\", \n\t\t\"sequence\":\"").append(f.toString()).append("\", \n\t\t\"mass\":")
                    .append(f.getMass()-Util.PROTON_MASS).append(",\n\t\t\"clusterInfo\":[").append(clusterInfos).append("\n\t\t], \n\t\t\"clusterIds\":[").append(MyArrayUtils.toString(cluster, ",")).
                    append("],\n\t\t\"class\":").append(fragmentClass(f)).append(",\n\t\t\"range\":[");
            
            // add the ranges
            if (baseFrag instanceof CrosslinkedFragment) {
                CrosslinkedFragment cf = (CrosslinkedFragment) baseFrag;
                Fragment ff = cf.getBaseFragment();
                Fragment sf = cf.getCrossLinkedFragment();
                sb.append("\n\t\t\t{\"peptideId\":").append(ff.getPeptide() == peps[0]?0:1)
                        .append(", \"from\":").append(ff.getStart()).append(",\"to\":").append(ff.getEnd()).append("},");
                sb.append("\n\t\t\t{\"peptideId\":").append(sf.getPeptide() == peps[0]?0:1)
                        .append(", \"from\":").append(sf.getStart()).append(",\"to\":").append(sf.getEnd()).append("}");
            } else {
                sb.append("\n\t\t\t{\"peptideId\":").append(f.getPeptide() == peps[0]?0:1)
                        .append(", \"from\":").append(f.getStart()).append(",\"to\":").append(f.getEnd()).append("}");
            }
            sb.append("\n\t\t\t]\n\t},");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append("\n]");
    }

    protected StringBuilder fragmentClusterInfos(ArrayList<Integer> cluster, ArrayList<Cluster> clusters, HashMapArrayList<Fragment, SpectraPeakMatchedFragment> framentMatches, Fragment f, RunConfig conf, String indent) {
        ToleranceUnit tu = conf.getFragmentTolerance();
        String tuUnit = tu.getUnit();
        StringBuilder clusterInfos= new StringBuilder();
        for (int cid : cluster) {
            // get the cluster
            Cluster c = clusters.get(cid);
            
            boolean matchedMissing = false;
            double error =0;
            double expMz = c.mz;
            double calcMZ = 0;
            // find a match that fits to the cluster
            for (SpectraPeakMatchedFragment spmf : framentMatches.get(f)) {
                if (spmf.getCharge() == c.charge) {
                    matchedMissing = spmf.matchedMissing();
                    if (matchedMissing) {
                        calcMZ=spmf.getMZ()+Util.C13_MASS_DIFFERENCE/c.charge;
                    } else {
                        calcMZ = spmf.getMZ();
                    }
                    break;
                }
            }
 //           String errorS = conf.getFragmentTolerance().toString(calcMZ, expMz);
            clusterInfos.append(indent).append("{\"Clusterid\":").append(cid)
                    .append(",\"calcMZ\":").append(calcMZ)
                    .append(",\"error\":").append(tu.getError(calcMZ, expMz))
                    .append(",\"errorUnit\":\"").append(tuUnit);
            if (matchedMissing)
                clusterInfos.append("\",\"matchedMissingMonoIsotopic\": 1");
            else
                clusterInfos.append("\",\"matchedMissingMonoIsotopic\": 0");
            clusterInfos.append("},");
        }
        clusterInfos.deleteCharAt(clusterInfos.length()-1);
        return clusterInfos;
    }

    /**
     * add the Peaks to the JSON
     * @param spectrum
     * @param peaksCluster
     * @param sbCluster
     * @param cID
     * @param framentMatches
     * @param fragmentCluster
     * @param sb
     * @param cluster 
     */
    protected void addPeaks(Spectra spectrum, HashMapArrayList<SpectraPeak, Integer> peaksCluster, StringBuilder sbCluster, int cID, HashMapArrayList<Fragment,SpectraPeakMatchedFragment> framentMatches, HashMapArrayList<Fragment, Integer> fragmentCluster, StringBuilder sb, ArrayList<Cluster> cluster) {
        int peakID=-1;
        // now go through all peaks and add them to the json
        for (SpectraPeak sp : spectrum) {
            peakID++;
            ArrayList<Integer> clusters = peaksCluster.get(sp);
            if (clusters == null) {
                IntArrayList cids = new IntArrayList(1);
                if (sp.getMatchedAnnotation().size() > 0) {
                    // seems like we have a single peak anotation
                    for (SpectraPeakMatchedFragment spf : sp.getMatchedAnnotation()) {
                        // so we create a cluster for this peak
                        sbCluster.append("\n\t{\"charge\":"+ spf.getCharge() +",\"firstPeakId\":"+ peakID +"},");
                        cids.add(++cID);
                        fragmentCluster.add(spf.getFragment(),cID);
                        framentMatches.add(spf.getFragment(), spf);
                        cluster.add(new Cluster(cID, spf.getCharge(),sp.getMZ()));
                    }
                    sb.append("\n\t{\"mz\":").append(sp.getMZ()).append(", \"intensity\":").append(sp.getIntensity()).append(", \"clusterIds\":[").append(MyArrayUtils.toString(cids, ",")).append("]},");
                } else {
                    sb.append("\n\t{\"mz\":"+sp.getMZ() + ", \"intensity\":" + sp.getIntensity() + ", \"clusterIds\":[]}," );
                }
            }else
                sb.append("\n\t{\"mz\":").append(sp.getMZ()).append(", \"intensity\":").append(sp.getIntensity()).append(", \"clusterIds\":[").append(MyArrayUtils.toString(clusters, ",")).append("]},");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append("\n],");
    }

    /**
     * get all the cluster - information
     * @param spectrum
     * @param peak2ID
     * @param sbCluster
     * @param framentMatches
     * @param fragmentCluster
     * @param peaksCluster
     * @param clusters
     * @return 
     */
    protected int extractCluster(Spectra spectrum, HashMap<SpectraPeak, Integer> peak2ID, StringBuilder sbCluster,HashMapArrayList<Fragment,SpectraPeakMatchedFragment> framentMatches, HashMapArrayList<Fragment, Integer> fragmentCluster, HashMapArrayList<SpectraPeak, Integer> peaksCluster, ArrayList<Cluster> clusters) {
        int peakID=-1;
        for (SpectraPeak sp : spectrum) {
            peak2ID.put(sp, ++peakID);
        }
        // get the cluster for annotations
        
        int cID = -1;
        for (SpectraPeakCluster spc : spectrum.getIsotopeClusters()) {
            int charge = spc.getCharge();
            double mz = spc.getMZ();
            // add it to the output
            sbCluster.append("\n\t{\"charge\":").append(charge).append(",\"firstPeakId\":").append(peak2ID.get(spc.getMonoIsotopic())).append("},");
            spc.setDBid(++cID);
            
            clusters.add(new xiAnnotator.Cluster(cID, charge,spc.getMZ()));

            
            // did we have anything matched to this peak that fits the current cluster
            SpectraPeak sp0 = spc.get(0);
            for (SpectraPeakAnnotation spa : sp0.getMatchedAnnotation()) {
                if (spa instanceof SpectraPeakMatchedFragment) {
                    if (((SpectraPeakMatchedFragment)spa).getCharge() ==  charge) {
                        // yes
                        Fragment f =((SpectraPeakMatchedFragment)spa).getFragment();
                        fragmentCluster.add(f, cID);
                        framentMatches.add(f, ((SpectraPeakMatchedFragment)spa));
                    }
                }
            }
            // assign the peaks to this cluster
            for (SpectraPeak sp : spc) {
                peaksCluster.add(sp, cID);
            }
        }
        return cID;
    }

    /**
     * add the link-site information to the JSON
     * @param sb
     * @param peps
     * @param match 
     */
    protected void addLinks(StringBuilder sb, Peptide[] peps, MatchedXlinkedPeptide match) {
        sb.append("\"LinkSite\" : [");
        if (peps.length>1) {
            sb.append("\n\t{\"peptideId\":0,\"linkSite\":");
            sb.append(match.getLinkingSite(0));
            sb.append(",\"linkWeight\":[");
            double[] w = new double[peps[0].length()];
            System.arraycopy(((MatchedXlinkedPeptideWeighted)match).getLinkageWeights(0), 0, w, 0, w.length);
            sb.append(MyArrayUtils.toString( w,",") );
            sb.append("]},\n\t{\"peptideId\":1,\"linkSite\":");
            sb.append(match.getLinkingSite(1));
            sb.append(",\"linkWeight\":[");

            w = new double[peps[1].length()];
            System.arraycopy(((MatchedXlinkedPeptideWeighted)match).getLinkageWeights(1), 0, w, 0, w.length);
            sb.append(MyArrayUtils.toString( w,",") );
            sb.append("]}");
        }
        sb.append("\n],");
    }

    /**
     * Turn the peptides into JSON and add them to the StringBuilder
     * @param sb
     * @param peps 
     */
    protected void addPeptides(StringBuilder sb, Peptide[] peps) {
        // add the peptides
        sb.append("\"Peptides\":[\n\t{\n\t\t\"sequence\":[\n\t\t\t");
        AminoAcid aa = peps[0].aminoAcidAt(0);
        appendAA(aa, sb);
        for (int a =1; a<peps[0].length();a++) {
            sb.append(",\n\t\t\t");
            appendAA(peps[0].aminoAcidAt(a), sb);
        }
        if (peps.length>1) {
            sb.append("\n\t\t]\n\t},\n\t{\n\t\t\"sequence\":[\n\t\t\t");
            aa = peps[1].aminoAcidAt(0);
            appendAA(aa, sb);
            for (int a =1; a<peps[1].length();a++) {
                sb.append(",\n\t\t\t");
                appendAA(peps[1].aminoAcidAt(a), sb);
            }
        }
        //link sites
        sb.append("\n\t\t]\n\t}\n],\n");
    }
    
    /**
     * declares what the class of a fragment is - currently anything not a straight a,b,c,x,y or z Ion will be declared as loss
     * @param f
     * @return 
     */
    protected String fragmentClass(Fragment f) {
        if (f.isClass(Loss.class) || f.isClass(BLikeDoubleFragmentation.class)){
            return "\"lossy\"";
        } else if (f instanceof CrosslinkedFragment) {
            CrosslinkedFragment xf = (CrosslinkedFragment) f;
            if (!xf.isClass( PeptideIon.class)) {
                return "\"lossy\"";
            }
        }
        return "\"non-lossy\"";
    }

    /**
     * collects all fragment-type information for a fragment.
     * @param f the fragment
     * @param sb the types will appended to the StringBuilder
     */
    protected void FragmentType(Fragment f, StringBuilder sb) {
        if (f instanceof Loss){
            sb.append("Loss,");
            FragmentType(((Loss)f).getBaseFragment(), sb);
        } else if (f instanceof CrosslinkedFragment) {
            sb.append("CrossLink(");
            CrosslinkedFragment xf = (CrosslinkedFragment) f;
            FragmentType(xf.getBaseFragment(),sb);
            sb.append("|");
            FragmentType(xf.getCrossLinkedFragment(),sb);
            sb.append(")");
        } else {
            sb.append(f.getClass().getSimpleName());
        }
     }
    
    /**
     * turns an amino-acid into a JSON string and appends it to the StringBuilder.
     * @param aa
     * @param sb 
     */
    protected void appendAA(AminoAcid aa, StringBuilder sb) {
        if (aa instanceof AminoModification) {
            
            AminoAcid base = ((AminoModification)aa).BaseAminoAcid;
            sb.append("{\"aminoAcid\":\"");
            sb.append(base.SequenceID);
            sb.append("\", \"Modification\":\"");
            if (((AminoModification)aa).SequenceID.startsWith(base.SequenceID)) {
                sb.append(((AminoModification)aa).SequenceID.substring(base.SequenceID.length()));
            } else
                sb.append(((AminoModification)aa).SequenceID);
            sb.append("\"}");
            
        } else if (aa instanceof AminoLabel) {
            
            AminoAcid base = ((AminoLabel)aa).BaseAminoAcid;
            sb.append("{\"aminoAcid\":\"");
            sb.append(base.SequenceID);
            sb.append(", \"Modification\":\"");
            if (((AminoLabel)aa).SequenceID.startsWith(base.SequenceID)) {
                sb.append(((AminoLabel)aa).SequenceID.substring(base.SequenceID.length()));
            } else
                sb.append(((AminoLabel)aa).SequenceID);
            sb.append("\"}");
        } else {
            sb.append("{\"aminoAcid\":\"");
            sb.append(aa.SequenceID);
            sb.append("\",\"Modification\":\"\"}");
        }
    }
    
    /**
     * creates the the match
     * @param spectrum
     * @param peps
     * @param links
     * @param config
     * @return 
     */
    protected MatchedXlinkedPeptide getMatch(Spectra spectrum, Peptide[] peps, List<Integer> links, RunConfig config,int firstResidue) {
            MatchedXlinkedPeptide match = null;
            if (peps.length > 1) {
                Logger.getLogger(this.getClass().getName()).log(Level.FINE, "xl match ");
                Logger.getLogger(this.getClass().getName()).log(Level.FINE, "Pep1: " + peps[0]);
                Logger.getLogger(this.getClass().getName()).log(Level.FINE, "Pep2: " +peps[1]);
                double mass = spectrum.getPrecurserMass();
                for (Peptide p : peps) 
                    mass-=p.getMass();
                CrossLinker xl = config.getCrossLinker().get(0);
                double diff =  Math.abs(xl.getCrossLinkedMass()-mass);
                for (CrossLinker xlt : config.getCrossLinker()) {
                    double tdif = Math.abs(xlt.getCrossLinkedMass()-mass);;
                    if (tdif < diff) {
                        xl = xlt;
                        diff=tdif;
                    }
                }
                Logger.getLogger(this.getClass().getName()).log(Level.FINE, "new match ");
                match = new MatchedXlinkedPeptideWeighted(spectrum, peps[0], peps[1], xl, config);
            } else {
                match = new MatchedXlinkedPeptide(spectrum, peps[0], null, null, config);
            }
            
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "to the actual matching ");
            if (links.size()>1) 
                    match.matchPeptides(links.get(0)-firstResidue,links.get(1)-firstResidue);
            else 
                match.matchPeptides();
            
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "return match ");
            return match;
    }
    
    
    /**
     * Just turns an exception into a string - as the name says.
     * 
     * @param e
     * @return string representation of the exception
     */
    public String exception2String(Exception e){
                    StringBuilder sbError = new StringBuilder();
                    sbError.append(e.toString()).append("\n");
                    
                    for (StackTraceElement ste :e.getStackTrace()) {
                        sbError.append(ste.toString() +"\n");
                    }
                    return sbError.toString();       
    }
}
