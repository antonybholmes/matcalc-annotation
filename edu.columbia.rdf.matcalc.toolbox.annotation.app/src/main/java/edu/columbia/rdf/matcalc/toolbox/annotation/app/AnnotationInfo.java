package edu.columbia.rdf.matcalc.toolbox.annotation.app;

import org.jebtk.core.AppVersion;
import org.jebtk.modern.UIService;
import org.jebtk.modern.help.GuiAppInfo;

public class AnnotationInfo extends GuiAppInfo {

  public AnnotationInfo() {
    super("Annotation", new AppVersion(2),
        "Copyright (C) 2016-2016 Antony Holmes",
        UIService.getInstance().loadIcon(AnnotationIcon.class, 32),
        UIService.getInstance().loadIcon(AnnotationIcon.class, 128),
        "Annotate genomic regions.");
  }

}
