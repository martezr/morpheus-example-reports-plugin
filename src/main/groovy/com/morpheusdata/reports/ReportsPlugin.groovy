package com.morpheusdata.reports

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission

class ReportsPlugin extends Plugin {

	@Override
	void initialize() {
		this.setName("Custom Cypher Report")
		this.setDescription("A custom report plugin for cypher items")
		this.setAuthor("Martez Reed")
		this.setSourceCodeLocationUrl("https://github.com/martezr/morpheus-example-reports-plugin")
		this.setIssueTrackerUrl("https://github.com/martezr/morpheus-example-reports-plugin/issues")
		CustomReportProvider customReportProvider = new CustomReportProvider(this, morpheus)
		this.pluginProviders.put(customReportProvider.code, customReportProvider)
	}

	@Override
	void onDestroy() {
	}

	@Override
	public List<Permission> getPermissions() {
		// Define the available permissions for the report
		Permission permission = new Permission('Custom Cypher Report', 'customCypherReport', [Permission.AccessType.none, Permission.AccessType.full])
		return [permission];
	}
}