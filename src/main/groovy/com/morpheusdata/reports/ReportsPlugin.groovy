package com.morpheusdata.reports

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission

class ReportsPlugin extends Plugin {

	@Override
	String getCode() {
		return 'custom-report-example'
	}

	@Override
	void initialize() {
		this.setName("Custom Report Examples")
		this.setDescription("Example Morpheus custom reports")
		this.setAuthor("Martez Reed")
		this.setSourceCodeLocationUrl("https://github.com/martezr/morpheus-example-reports-plugin")
		this.setIssueTrackerUrl("https://github.com/martezr/morpheus-example-reports-plugin/issues")
		CypherReportProvider cypherReportProvider = new CypherReportProvider(this, morpheus)
		this.pluginProviders.put(cypherReportProvider.code, cypherReportProvider)
		RestApiReportProvider restApiReportProvider = new RestApiReportProvider(this, morpheus)
		this.pluginProviders.put(restApiReportProvider.code, restApiReportProvider)
		UserProvisioningHistoryReportProvider userProvisioningHistoryReportProvider = new UserProvisioningHistoryReportProvider(this, morpheus)
		this.pluginProviders.put(userProvisioningHistoryReportProvider.code, userProvisioningHistoryReportProvider)
		WorkloadUsageReportProvider workloadUsageReportProvider = new WorkloadUsageReportProvider(this, morpheus)
		this.pluginProviders.put(workloadUsageReportProvider.code, workloadUsageReportProvider)
	}

	@Override
	void onDestroy() {
	}

	@Override
	public List<Permission> getPermissions() {
		// Define the available permissions for the report
		Permission permission = new Permission('Custom Example Reports', 'customExampleReports', [Permission.AccessType.none, Permission.AccessType.full])
		return [permission];
	}
}
