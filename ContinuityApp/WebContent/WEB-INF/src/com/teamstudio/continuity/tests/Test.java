package com.teamstudio.continuity.tests;

import java.util.Date;

public interface Test {

	public String getDescription();
	public boolean isSuccess();
	public String getMessage();
	public Date getRunAt();
	public boolean isRun();
	public void execute();
	
}
