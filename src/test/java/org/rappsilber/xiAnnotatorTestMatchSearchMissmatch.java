package org.rappsilber;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.grizzly.http.server.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class xiAnnotatorTestMatchSearchMissmatch {

    private HttpServer server;
    private WebTarget target;

    @Before
    public void setUp() throws Exception {
        // start the server
        server = Main.startServer();
        // create the client
        Client c = ClientBuilder.newClient();

        // uncomment the following line if you want to enable
        // support for JSON in the client (you also have to uncomment
        // dependency on jersey-media-json module in pom.xml and Main.startServer())
        // --
        // c.configuration().enable(new org.glassfish.jersey.media.json.JsonJaxbFeature());

        target = c.target(Main.BASE_URI);
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    /**
     * Test to see that the message "Got it!" is sent in the response.
     */
    @Test
    public void testGetIt() {
/*        String responseMsg = target.path("annotate/3413/68913-53595-83402-24933/210231825/")
                .queryParam("peptide","TVTAMDVVYALK","TLYGFGG")
                .queryParam("link","4","6")
                .queryParam("custom","fragment:BLikeDoubleFragmentation;ID:4")
                .request().get(String.class);
        String responseMsg = target.path("annotate/3421/85160-94827-96653-69142/210313888/") 
                .queryParam("peptide","LVRPEVDVMCcmTAFHDNEETFLKK","YKAAFTECcmCcmQAADK")
                .queryParam("link","21","1")
                .queryParam("custom","fragment:BLikeDoubleFragmentation;ID:4")
                .request().get(String.class);
        String responseMsg = target.path("annotate/3421/85160-94827-96653-69142/210312725/") 
                .queryParam("peptide","VHTECcmCcmHGDLLECcmADDRADLAK")
                .queryParam("custom","fragment:BLikeDoubleFragmentation;ID:4")
                .request().get(String.class);
        String responseMsg = target.path("annotate/3947/30582-35562-38444-85066/227540102/") 
                .queryParam("peptide","GSSHHHHHHSSGLVPR","XdLFSK")
                .queryParam("link","4","1")
                .queryParam("firstresidue","0")
                .queryParam("custom","fragment:BLikeDoubleFragmentation;ID:4")
                .request().get(String.class);
*///        http://129.215.14.63/xiAnnotator/annotate/3943/21672-50578-53639-62223/227500098/?peptide=GSSHHHHHHSSGLVPR&peptide=XdLFSK&link=4&link=1
//        String responseMsg = target.path("annotate/10250/57056-70694-16717-76186/1165236018/") 
//                .queryParam("peptide","EFLENYLLTDEGLEAVNK")
//                .queryParam("peptide","EFLENYLLTDEGLEAVNK")
//                .request().get(String.class);
        //http://xi3.bio.ed.ac.uk/xiAnnotator/annotate/11634/77974-01158-25864-79223/3938059811/?peptide=HIQKEDVPSER
//        String responseMsg = target.path("annotate/11634/77974-01158-25864-79223/3938059811/") 
//                .queryParam("peptide","HIQKEDVPSER")
//                .request().get(String.class);
//http://xi3.bio.ed.ac.uk/xiAnnotator/annotate/11996/11003-13921-38126-65897/4440116871/?peptide=LAEVAAKESIK&peptide=YRPTKFSDTVGQDSIK&link=7&link=5
        String responseMsg = target.path("annotate/11996/11003-13921-38126-65897/4440116871/") 
                .queryParam("peptide","LAEVAAKESIK","YRPTKFSDTVGQDSIK")
                .queryParam("link","7","3")
                .request().get(String.class);
        //10288/18135-52064-38377-95239/1205418326/?peptide=QEPERNECFLQHKDDNPNLPR&peptide=HKPKATKEQLKdtsspohAVMDDFAAFVEKCCK&link=13&link=7
//                .queryParam("custom","fragment:BLikeDoubleFragmentation;ID:4")
        System.out.println(responseMsg);
        assertEquals("{\"error\":\"No Spectra\"}", responseMsg);
    }
}
