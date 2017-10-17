package edu.columbia.rdf.matcalc.toolbox.annotation.app;


import java.awt.FontFormatException;
import java.io.IOException;

import javax.swing.UnsupportedLookAndFeelException;
import javax.xml.parsers.ParserConfigurationException;

import org.jebtk.core.AppService;
import org.jebtk.modern.ColorTheme;
import org.jebtk.modern.theme.ThemeService;
import org.xml.sax.SAXException;

import edu.columbia.rdf.matcalc.MainMatCalc;
import edu.columbia.rdf.matcalc.ModuleLoader;
import edu.columbia.rdf.matcalc.bio.BioModuleLoader;
import edu.columbia.rdf.matcalc.toolbox.annotation.AnnotationModule;




public class MainAnnotation {
	//private static final Logger LOG = 
	//		LoggerFactory.getLogger(MainSeqLogo.class);
	
	public static final void main(String[] args) throws FontFormatException, IOException, SAXException, ParserConfigurationException, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
		AppService.getInstance().setAppInfo("annotation");
		
		ThemeService.getInstance().setTheme(ColorTheme.RED);
		
		ModuleLoader ml = new BioModuleLoader().addModule(AnnotationModule.class);
		
		MainMatCalc.main(new AnnotationInfo(), ml);
	}
}
