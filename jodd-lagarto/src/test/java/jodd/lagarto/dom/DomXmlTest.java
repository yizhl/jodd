// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.lagarto.dom;

import jodd.io.FileUtil;
import jodd.util.StringUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DomXmlTest {
	protected String testDataRoot;

	@Before
	public void setUp() throws Exception {
		if (testDataRoot != null) {
			return;
		}
		URL data = NodeSelectorTest.class.getResource("test");
		testDataRoot = data.getFile();
	}

	@Test
	public void testPeopleXml() throws IOException {
		File file = new File(testDataRoot, "people.xml");
		String xmlContent = FileUtil.readString(file);

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		Document doc = lagartoDOMBuilder.parse(xmlContent);

		assertEquals(2, doc.getChildNodesCount());    // not 3!

		XmlDeclaration xml = (XmlDeclaration) doc.getFirstChild();
		assertEquals(3, xml.getAttributesCount());

		Element peopleList = (Element) doc.getChild(1);
		assertEquals(1, peopleList.getChildNodesCount());

		Element person = peopleList.getFirstChildElement();
		assertEquals(3, person.getChildNodesCount());

		Element name = (Element) person.getChild(0);
		assertEquals("Fred Bloggs", name.getTextContent());
		assertEquals("Male", person.getChild(2).getTextContent());

		xmlContent = StringUtil.removeChars(xmlContent, "\n\r\t");
		assertEquals(xmlContent, doc.getHtml());

		assertTrue(doc.check());
	}

	@Test
	public void testUpheaWebXml() throws IOException {
		File file = new File(testDataRoot, "uphea-web.xml");
		String xmlContent = FileUtil.readString(file);

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		Document doc = lagartoDOMBuilder.parse(xmlContent);

		xmlContent = StringUtil.removeChars(xmlContent, "\n\r\t");
		assertEquals(xmlContent, doc.getHtml());

		assertTrue(doc.check());
	}

	@Test
	public void testWhitespaces() throws IOException {
		String xmlContent = "<foo>   <!--c-->  <bar>   </bar> <x/> </foo>";

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		lagartoDOMBuilder.setSelfCloseVoidTags(true);

		Document doc = lagartoDOMBuilder.parse(xmlContent);

		assertEquals(1, doc.getChildNodesCount());

		Element foo = (Element) doc.getChild(0);
		assertEquals("foo", foo.getNodeName());

		assertEquals(3, foo.getChildNodesCount());
		Element bar = (Element) foo.getChild(1);
		assertEquals("bar", bar.getNodeName());

		assertEquals(1, bar.getChildNodesCount());    // must be 1 as whitespaces are between open/closed tag

		assertEquals("<foo><!--c--><bar>   </bar><x/></foo>", doc.getHtml());

		assertTrue(doc.check());
	}

	@Test
	public void testIgnoreComments() throws IOException {
		String xmlContent = "<foo>   <!--c-->  <bar>   </bar> <!--c--> <x/> <!--c--> </foo>";

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		lagartoDOMBuilder.setIgnoreComments(true);

		Document doc = lagartoDOMBuilder.parse(xmlContent);

		assertEquals("<foo><bar>   </bar><x></x></foo>", doc.getHtml());

		assertTrue(doc.check());
	}

	@Test
	public void testConditionalComments() throws IOException {
		String xmlContent = "<foo><!--[if !IE]>--><bar>Jodd</bar><!--<![endif]--></foo>";

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		lagartoDOMBuilder.setIgnoreComments(true);

		Document doc = lagartoDOMBuilder.parse(xmlContent);

		assertEquals("<foo><bar>Jodd</bar></foo>", doc.getHtml());

		assertTrue(doc.check());
	}

	@Test
	public void testConditionalComments2() throws IOException {
		String xmlContent = "<foo><![if !IE]><bar>Jodd</bar></foo>";

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		lagartoDOMBuilder.setIgnoreComments(true);
		lagartoDOMBuilder.setCollectErrors(true);
		lagartoDOMBuilder.setCalculatePosition(true);

		Document doc = lagartoDOMBuilder.parse(xmlContent);
		List<String> errors = doc.getErrors();

		assertEquals(1, errors.size());
		assertTrue(errors.get(0).contains("[1:5 @5]"));
		assertEquals("<foo><bar>Jodd</bar></foo>", doc.getHtml());

		assertTrue(doc.check());
	}

	@Test
	public void testAddDeleteModifyNode() throws IOException {
		File file = new File(testDataRoot, "people.xml");
		String xmlContent = FileUtil.readString(file);

		LagartoDOMBuilder lagartoDOMBuilder = new LagartoDOMBuilder();
		lagartoDOMBuilder.enableXmlMode();
		Document xml = lagartoDOMBuilder.parse(xmlContent);

		// find all persons
		NodeSelector nodeSelector = new NodeSelector(xml);
		List<Node> persons = nodeSelector.select("person");

		assertEquals(1, persons.size());
		Node man = persons.get(0);
		assertEquals("Fred Bloggs", man.getChild(0).getTextContent());

		// update
		man.getChild(0).getChild(0).setNodeValue("Just Joe");

		// append
		Element newPerson = new Element(xml, "person", false, false);
		newPerson.addChild(new Element(xml, "name", false, false));
		newPerson.getChild(0).addChild(new Text(xml, "Just Maria"));

		man.getParentNode().addChild(newPerson);

		xmlContent = xml.getHtml();	// yeah, its XML content

		assertEquals(
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
				"<people_list>" +
					"<person>" +
						"<name>Just Joe</name>" +
						"<birthdate>2008-11-27</birthdate>" +
						"<gender>Male</gender>" +
					"</person>" +
					"<person>" +
						"<name>Just Maria</name>" +
					"</person>" +
				"</people_list>", xmlContent);
	}
}
