package org.frasermccrossan.ltc;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* a very simple time representation class since we don't really want the sophistication
 * of Calendar; really just acts as a return value of a regexp-parsed time
 */

public class HourMinute {
	
	public static final int AM = 0;
	public static final int PM = 2;
	public static final int NO_AMPM = -1;
	
	static final int DAY_MINUTES = 24 * 60;
	static final int HALF_DAY_MINUTES = DAY_MINUTES / 2;
	
	/* this needs to match the following forms and extract the hour, minutes and am/pmage:
	 * "12:04"
	 * "12:04 [AP]"
	 * :12:04:08 [AP]"
	 */
	static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})(:\\d{2})? ?([AP])?");
	
	public int hour = -1;
	public int minute;
	public int am_pm = NO_AMPM;
	
	HourMinute(String textTime) {
		Matcher m = TIME_PATTERN.matcher(textTime);
		if (m.find()) {
			hour=Integer.valueOf(m.group(1));
			if (hour == 12) { hour = 0; } // makes calculations easier
			minute = Integer.valueOf(m.group(2));
			String am_pm = m.group(3);
			if (m.group(3) != null) {
				am_pm = am_pm.toLowerCase();
				if (am_pm.equals("p")) {
					hour += 12; // we fixed the hour values above, so 12 is zero
				}
			}
		}
	}
	
	public boolean isValid() {
		return hour >= 0;
	}
	
	public Boolean hasAmPm() {
		return am_pm != NO_AMPM;
	}
	
	public Boolean isPm() {
		return am_pm == PM;
	}
	
	int timeDiff(Calendar reference) {
		if (isValid()) {
			if (hasAmPm()) {
				int minsAfterMidnight = (hour + (isPm() ? 12 : 0)) * 60 + minute;
				int refMinsAfterMidnight = reference.get(Calendar.HOUR_OF_DAY) * 60 + reference.get(Calendar.MINUTE);
				if (minsAfterMidnight >= refMinsAfterMidnight) {
					return minsAfterMidnight - refMinsAfterMidnight;
				}
				else {
					return DAY_MINUTES - refMinsAfterMidnight + minsAfterMidnight;
				}
			}
			else {
				// no am/pm indicator, assume it's within 12 hours of now and take a best guess
				int ref12Hour = reference.get(Calendar.HOUR);
				if (ref12Hour == 12) { ref12Hour = 0; }
				int minsAfterMid = hour * 60 + minute; // after midnight or midday, don't care
				int refMinsAfterMid = ref12Hour * 60 + reference.get(Calendar.MINUTE);
				if (minsAfterMid >= refMinsAfterMid) {
					// it looks like it's shortly after the current time
					return minsAfterMid - refMinsAfterMid;
				}
				else {
					// it looks like it has crossed the midday/midnight boundary
					return HALF_DAY_MINUTES - refMinsAfterMid + minsAfterMid;
				}
			}
		}
		else {
			return -1;
		}
	}
	
}
