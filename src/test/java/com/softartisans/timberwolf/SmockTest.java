package com.softartisans.timberwolf;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.axis2.AxisFault;
import com.softartisans.timberwolf.exchangeservice.ExchangeServiceStub;
import com.microsoft.schemas.exchange.services._2006.messages.FindItemDocument;
import com.microsoft.schemas.exchange.services._2006.messages.FindItemResponseDocument;
import com.microsoft.schemas.exchange.services._2006.types.ExchangeImpersonationDocument;
import com.microsoft.schemas.exchange.services._2006.types.MailboxCultureDocument;
import com.microsoft.schemas.exchange.services._2006.types.RequestServerVersionDocument;
import com.microsoft.schemas.exchange.services._2006.types.TimeZoneContextDocument;

import java.rmi.RemoteException;

/**
 * Unit test for simple App.
 */
public class SmockTest
    extends TestCase
{
    private static final String resourcePrefix = "com/softartisans/timberwolf/SmockTest/";
    private ExchangeServiceStub stub;


    public void setUp() {
        Smock.initialize();
        // client has to be created after createServer was called
        try {
            stub = new ExchangeServiceStub("http://glue:8080/axis2/services/Exchange");
        } catch (AxisFault axisFault) {
            axisFault.printStackTrace();
            Assert.fail("Could not create ExchangeServiceStub: " + axisFault);
        }
    }

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SmockTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( SmockTest.class );
    }

    public void testSmock() throws RemoteException {
//        mockServer.expect(message(resourcePrefix + "request1.xml")).
//            andRespond(withMessage(resourcePrefix + "response1.xml"));
        FindItemDocument fid = FindItemDocument.Factory.newInstance();
        ExchangeImpersonationDocument eid = ExchangeImpersonationDocument.Factory.newInstance();
        MailboxCultureDocument mcd = MailboxCultureDocument.Factory.newInstance();
        RequestServerVersionDocument rsvd = RequestServerVersionDocument.Factory.newInstance();
        TimeZoneContextDocument tzcd = TimeZoneContextDocument.Factory.newInstance();
        FindItemResponseDocument fir = stub.findItem(fid, null, null, null, null);
//        mockServer.verify();
        Assert.fail();
    }

    public void tearDown() {
        //        mockServer.verify();
    }

}