/**
 * Copyright (C) 2016, Antony Holmes
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. Neither the name of copyright holder nor the names of its contributors 
 *     may be used to endorse or promote products derived from this software 
 *     without specific prior written permission. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package edu.columbia.rdf.matcalc.toolbox.annotation;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jebtk.bioinformatics.ext.ucsc.Bed;
import org.jebtk.bioinformatics.ext.ucsc.BedRegion;
import org.jebtk.bioinformatics.ext.ucsc.UCSCTrack;
import org.jebtk.bioinformatics.ext.ucsc.UCSCTrackRegion;
import org.jebtk.bioinformatics.gapsearch.GapSearch;
import org.jebtk.bioinformatics.genomic.Chromosome;
import org.jebtk.bioinformatics.genomic.ChromosomeService;
import org.jebtk.bioinformatics.genomic.GenomicRegion;
import org.jebtk.bioinformatics.genomic.GenomicRegions;
import org.jebtk.core.Mathematics;
import org.jebtk.core.collections.CollectionUtils;
import org.jebtk.core.collections.UniqueArrayList;
import org.jebtk.core.io.FileUtils;
import org.jebtk.core.io.PathUtils;
import org.jebtk.core.text.TextUtils;
import org.jebtk.math.matrix.DataFrame;
import org.jebtk.modern.UIService;
import org.jebtk.modern.button.ModernButton;
import org.jebtk.modern.dialog.ModernDialogStatus;
import org.jebtk.modern.dialog.ModernMessageDialog;
import org.jebtk.modern.event.ModernClickEvent;
import org.jebtk.modern.event.ModernClickListener;
import org.jebtk.modern.ribbon.RibbonLargeButton;
import org.jebtk.modern.widget.tooltip.ModernToolTip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.columbia.rdf.matcalc.MainMatCalcWindow;
import edu.columbia.rdf.matcalc.toolbox.CalcModule;
import edu.columbia.rdf.matcalc.toolbox.annotation.app.AnnotationIcon;


/**
 * Merges designated segments together using the merge column. Consecutive rows with the same
 * merge id will be merged together. Coordinates and copy number will be adjusted but
 * genes, cytobands etc are not.
 *
 * @author Antony Holmes Holmes
 *
 */
public class AnnotationModule extends CalcModule {	
	private static final Logger LOG = 
			LoggerFactory.getLogger(AnnotationModule.class);

	private static final Path RES_FOLDER = 
			PathUtils.getPath("res/modules/annotation");

	/**
	 * The member window.
	 */
	private MainMatCalcWindow mWindow;

	private Map<String, Path> mBedFileMap = 
			new TreeMap<String, Path>();

	private Map<String, Map<String, String>> mDescriptionMap = 
			new TreeMap<String, Map<String, String>>();

	/* (non-Javadoc)
	 * @see org.abh.lib.NameProperty#getName()
	 */
	@Override
	public String getName() {
		return "Annotation";
	}

	@Override
	public void run(String... args) {
		for (String arg : args) {
			if (arg.equals("--switch-tab")) {
				mWindow.getRibbon().changeTab("Annotation");
			}
		}

		try {
			annotate();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see edu.columbia.rdf.apps.matcalc.modules.Module#init(edu.columbia.rdf.apps.matcalc.MainMatCalcWindow)
	 */
	@Override
	public void init(MainMatCalcWindow window) {
		mWindow = window;

		try {
			load();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ModernButton button = new RibbonLargeButton("Annotation", 
				UIService.getInstance().loadIcon(AnnotationIcon.class, 32),
				UIService.getInstance().loadIcon(AnnotationIcon.class, 24));
		
		button.setToolTip(new ModernToolTip("Annotation", 
				"Annotate regions."), mWindow.getRibbon().getToolTipModel());
		button.setClickMessage("Annotate");
		mWindow.getRibbon().getToolbar("Bioinformatics").getSection("Annotation").add(button);
		
		button.addClickListener(new ModernClickListener() {
			@Override
			public void clicked(ModernClickEvent e) {
				try {
					annotate();
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (ParseException e1) {
					e1.printStackTrace();
				}
			}});
		
		button = new RibbonLargeButton(UIService.getInstance().loadIcon("segment_size", 24));
		
		button.setToolTip(new ModernToolTip("Segment Size", 
				"Segment Size."), mWindow.getRibbon().getToolTipModel());
		button.setClickMessage("Segment Size");
		mWindow.getRibbon().getToolbar("Bioinformatics").getSection("Annotation").add(button);

		button.addClickListener(new ModernClickListener() {
			@Override
			public void clicked(ModernClickEvent e) {
				try {
					segmentSize();
				} catch (ParseException e1) {
					e1.printStackTrace();
				}
			}});
	}

	private void load() throws IOException {
		if (!FileUtils.exists(RES_FOLDER)) {
			return;
		}

		for (Path file : FileUtils.ls(RES_FOLDER)) {
			if (PathUtils.getName(file).contains("bed.gz")) {

				String name = null;

				try {
					name = UCSCTrack.getNameFromTrack(file);
				} catch (IOException e) {
					e.printStackTrace();
				}

				mBedFileMap.put(name, file);

				try {
					mDescriptionMap.put(name, UCSCTrack.getTrackAttributes(file));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Analysis.
	 * 
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws Exception 
	 */
	private void annotate() throws IOException, ParseException {
		DataFrame m = mWindow.getCurrentMatrix();

		// first find a location column
		int locationColumn = DataFrame.findColumn(m, "location");


		if (locationColumn != -1) {

			if (m.getRows() > 1) {
				if (!GenomicRegion.isGenomicRegion(m.getText(0, locationColumn)) && 
						!GenomicRegion.isGenomicRegion(m.getText(1, locationColumn))) {
					// If the column does not appear to contain coordinates
					locationColumn = -1;
				}
			} else if (m.getRows() == 1) {
				if (!GenomicRegion.isGenomicRegion(m.getText(0, locationColumn))) {
					// If the column does not appear to contain coordinates
					locationColumn = -1;
				}
			} else {
				locationColumn = -1;
			}
		}


		int chrCol = -1;
		int startCol = -1;
		int endCol = -1;

		if (locationColumn == -1) {
			// No location column so see if separate chr, start and end exist

			chrCol = DataFrame.findColumn(m, "chr");
			startCol = DataFrame.findColumn(m, "start");
			endCol = DataFrame.findColumn(m, "end");
		}

		if (locationColumn == -1 && chrCol == -1) {
			ModernMessageDialog.createWarningDialog(mWindow, 
					"The matrix does not appear to contain genomic coordinates.");

			return;
		}

		AnnotationDialog dialog = new AnnotationDialog(mWindow, 
				mBedFileMap, 
				mDescriptionMap);

		dialog.setVisible(true);

		if (dialog.getStatus() == ModernDialogStatus.CANCEL) {
			return;
		}

		// How many new columns to add
		int newColCount = m.getCols();

		for (AnnotationPanel panel : dialog.getPanels()) {
			if (!panel.getAddFeatures()) {
				continue;
			}

			if (panel.getAddAll()) {
				++newColCount;
			}

			if (panel.getCondense()) {
				++newColCount;
			}

			if (panel.getAddCount()) {
				++newColCount;
			}

			if (panel.getAddFirstN()) {
				++newColCount;
			}
		}

		DataFrame ret = DataFrame.createDataFrame(m.getRows(), newColCount);

		DataFrame.copy(m, ret);

		// Add some annotation

		int c = m.getCols();

		for (AnnotationPanel panel : dialog.getPanels()) {
			if (!panel.getAddFeatures()) {
				continue;
			}

			if (panel.getAddCount()) {
				ret.setColumnName(c++, "num." + panel.getName());
			}

			if (panel.getAddFirstN()) {
				ret.setColumnName(c++, "first." + panel.getFirstNCount() + "." + panel.getName());
			}

			if (panel.getCondense()) {
				ret.setColumnName(c++, "condensed." + panel.getName());
			}

			if (panel.getAddAll()) {
				ret.setColumnName(c++, panel.getName());
			}
		}

		// Load the bed files

		Map<String, UCSCTrack> bedMap = new HashMap<String, UCSCTrack>();

		for (AnnotationPanel panel : dialog.getPanels()) {
			if (!panel.getAddFeatures()) {
				continue;
			}

			LOG.info("Loading BED {}", mBedFileMap.get(panel.getName()));

			bedMap.put(panel.getName(), Bed.parseTrack(mBedFileMap.get(panel.getName())));
		}

		// Now for the annotation

		LOG.info("Annotating...");

		Chromosome chr;
		int start;
		int end;

		Map<UCSCTrack, GapSearch<UCSCTrackRegion>> gapMap =
				new HashMap<UCSCTrack, GapSearch<UCSCTrackRegion>>();

		UCSCTrack track;
		GapSearch<UCSCTrackRegion> gapSearch;

		for (int r = 0; r < m.getRows(); ++r) {
			GenomicRegion region;

			if (locationColumn != -1) {
				String t = m.getText(r, locationColumn);

				// Empty location so skip
				if (t == null) {
					continue;
				}

				region = GenomicRegion.parse(t);
			} else {
				chr = ChromosomeService.getInstance().parse(m.getText(r, chrCol));
				start = (int)m.getValue(r, startCol);
				end = (int)m.getValue(r, endCol);

				region = new GenomicRegion(chr, start, end);
			}

			if (region == null) {
				continue;
			}

			c = m.getCols();

			for (AnnotationPanel panel : dialog.getPanels()) {
				if (!panel.getAddFeatures()) {
					continue;
				}

				track = bedMap.get(panel.getName());

				// Find all features

				// Create a searchable index
				if (!gapMap.containsKey(track)) {

					LOG.info("Searching {} ...", track.getName());

					/*
					if (dialog.getClosestMode()) {
						gapSearch = GenomicRegions.getFixedGapSearch(track.getRegions());
					} else {
						gapSearch = GenomicRegions.getBinarySearch(track.getRegions());
					}
					 */

					gapSearch = GenomicRegions.getBinarySearch(track.getRegions());

					LOG.info("Index built");
					gapMap.put(track, gapSearch);
				}

				gapSearch = gapMap.get(track);

				List<UCSCTrackRegion> regions = gapSearch.getFeatureSet(region);

				List<String> ids = new UniqueArrayList<String>();

				if (!dialog.getClosestMode()) {
					// Everything that overlaps
					for (UCSCTrackRegion tr : regions) {
						if (GenomicRegion.overlaps(tr, region)) {

							if (panel.getAddLocations()) {
								ids.add(tr.getLocation());
							} else {
								ids.add(((BedRegion)tr).getName());
							}
						}
					}
				} else {
					for (UCSCTrackRegion tr : regions) {
						if (panel.getAddLocations()) {
							ids.add(tr.getLocation());
						} else {
							ids.add(((BedRegion)tr).getName());
						}
					}
				}

				if (panel.getAddAlphabetical()) {
					Collections.sort(ids);
				}

				if (panel.getAddCount()) {
					ret.set(r, c++, ids.size());
				}

				if (panel.getAddFirstN()) {
					ret.set(r, c++, TextUtils.scJoin(CollectionUtils.head(ids, panel.getFirstNCount())));
				}

				if (panel.getCondense()) {
					String v1 = ids.get(0);
					String v2 = ids.get(ids.size() - 1);

					if (panel.getAddLocations()) {
						// In locations mode we want to report the minimum and
						// maximum coordinates that we find so reparse the
						// coordinates and get the extreme start and end
						v1 = Integer.toString(GenomicRegion.parse(v1).getStart());

						v2 = Integer.toString(GenomicRegion.parse(v2).getEnd());
					}

					// If items at the extremes of the list are the same, there
					// is no point adding dashes
					if (v1.equals(v2)) {
						ret.set(r, c++, v1);
					} else {
						ret.set(r, c++, v1 + "--" + v2);
					}
				}

				if (panel.getAddAll()) {
					ret.set(r, c++, TextUtils.scJoin(ids));
				}
			}
		}

		mWindow.addToHistory("Annotated", ret);
	}
	
	/**
	 * Add the segment size.
	 * 
	 * @throws ParseException
	 */
	private void segmentSize() throws ParseException {
		DataFrame m = mWindow.getCurrentMatrix();

		// first find a location column
		int locationColumn = DataFrame.findColumn(m, "location");


		if (locationColumn != -1) {

			if (m.getRows() > 1) {
				if (!GenomicRegion.isGenomicRegion(m.getText(0, locationColumn)) && 
						!GenomicRegion.isGenomicRegion(m.getText(1, locationColumn))) {
					// If the column does not appear to contain coordinates
					locationColumn = -1;
				}
			} else if (m.getRows() == 1) {
				if (!GenomicRegion.isGenomicRegion(m.getText(0, locationColumn))) {
					// If the column does not appear to contain coordinates
					locationColumn = -1;
				}
			} else {
				locationColumn = -1;
			}
		}


		int chrCol = -1;
		int startCol = -1;
		int endCol = -1;

		if (locationColumn == -1) {
			// No location column so see if separate chr, start and end exist

			chrCol = DataFrame.findColumn(m, "chr");
			startCol = DataFrame.findColumn(m, "start");
			endCol = DataFrame.findColumn(m, "end");
		}

		if (locationColumn == -1 && chrCol == -1) {
			ModernMessageDialog.createWarningDialog(mWindow, 
					"The matrix does not appear to contain genomic coordinates.");

			return;
		}

		DataFrame ret = 
				DataFrame.createDataFrame(m.getRows(), m.getCols() + 1);

		DataFrame.copy(m, ret);
		
		ret.setColumnName(m.getCols(), "segment.size.kb");

		// Now for the annotation

		Chromosome chr;
		int start;
		int end;

		for (int r = 0; r < m.getRows(); ++r) {
			GenomicRegion region;

			if (locationColumn != -1) {
				String t = m.getText(r, locationColumn);

				// Empty location so skip
				if (t == null) {
					continue;
				}

				region = GenomicRegion.parse(t);
			} else {
				chr = ChromosomeService.getInstance().parse(m.getText(r, chrCol));
				start = (int)m.getValue(r, startCol);
				end = (int)m.getValue(r, endCol);

				region = new GenomicRegion(chr, start, end);
			}

			if (region == null) {
				continue;
			}
			
			ret.set(r, m.getCols(), Mathematics.round((double)region.getLength() / 1000, 2));
		}

		mWindow.addToHistory("Segment size", ret);
	}
}
