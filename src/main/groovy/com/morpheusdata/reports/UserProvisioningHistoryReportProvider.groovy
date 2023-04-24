package com.morpheusdata.reports

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportType
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.response.ServiceResponse
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactivex.Observable;
import java.sql.Connection
import java.time.LocalDate;
import groovy.json.*

@Slf4j
class UserProvisioningHistoryReportProvider extends AbstractReportProvider {
	Plugin plugin
	MorpheusContext morpheusContext

	UserProvisioningHistoryReportProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	MorpheusContext getMorpheus() {
		morpheusContext
	}

	@Override
	Plugin getPlugin() {
		plugin
	}

	@Override
	String getCode() {
		'user-provisioning-history'
	}

	@Override
	String getName() {
		'User Provisioning History'
	}

	 ServiceResponse validateOptions(Map opts) {
		 return ServiceResponse.success()
	 }


	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<String> model = new ViewModel<String>()
		def HashMap<String, String> reportPayload = new HashMap<String, String>();

		// Add web nonce to allow the use of javascript scripts
		def webnonce = morpheus.getWebRequest().getNonceToken()
		reportPayload.put("webnonce",webnonce)

		// Pass report data to the hbs render
		reportPayload.put("reportdata",reportRowsBySection)
		model.object = reportPayload
		getRenderer().renderTemplate("hbs/userProvisioningHistory", model)
	}

	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp
	}

	void process(ReportResult reportResult) {
		// Update the status of the report (generating) - https://developer.morpheusdata.com/api/com/morpheusdata/model/ReportResult.Status.html
		morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
		Long displayOrder = 0
		List<GroovyRowResult> results = []
        List<GroovyRowResult> userAccounts = []
        def monthPayload = []
        //def usersPayload = [:].withDefault {}
        def usersPayload = []
        def outputPayload = []
        def sanitizedOutput = []
		Connection dbConnection

        LocalDate now = LocalDate.now(); // 2015-11-24

        for (int i = 0; i < 12; i++) {
            LocalDate earlier = now.minusMonths(i); // 2015-10-24

            def monthdata = ""
            //monthdata = earlier.getMonth().getValue() + "/" + earlier.getYear()
            def monthValue = earlier.getMonth().getValue()
            def monthSet
            if (monthValue < 10){
                monthSet = "0${monthValue}"
            } else {
                monthSet = monthValue
            }
            monthdata = earlier.getYear() + "-" + monthSet
            //monthPayload << earlier.getMonth()
            monthPayload << monthdata
        }
        monthPayload = monthPayload.reverse()

		try {
			// Create a read-only database connection
			dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
			results = new Sql(dbConnection).rows("SELECT u.username, DATE_FORMAT(a.date_created, '%Y-%m') yearmonth, COUNT(*) counter FROM audit_log a INNER JOIN user u ON a.user_id = u.id and description = 'Instance Created.' GROUP BY u.username, yearmonth;")
            userAccounts = new Sql(dbConnection).rows("SELECT * FROM morpheus.user;")
		} finally {
			// Close the database connection
			morpheus.report.releaseDatabaseConnection(dbConnection)
		}
        userAccounts.each{
            def emptyMap = [:]
            emptyMap["username"] = it.username
            emptyMap["data"] = []
            usersPayload << emptyMap
        }

        results.each{
            def emptyMap = [:]
            emptyMap[it.yearmonth] = it.counter
            usersPayload.each{ user ->
                if (user.username == it.username){
					user.data << emptyMap
				}
            }
        }

        usersPayload.each{ userdata ->
            def emptyMap = [:]
            emptyMap["username"] = userdata.username
            def total = 0
            // iterate over months and match data
            // if empty then seed with 0
            monthPayload.each{ month ->
                emptyMap[month] = 0
                userdata["data"].each{ monthData ->
                    monthData.each{ it ->
                        if (it.key == month){
                            emptyMap[month] = it.value
                        }
                    }
                }
                total += emptyMap[month]
            }
            emptyMap["total"] = total
            outputPayload << emptyMap
        }


        def sortedPayload = outputPayload.sort { -it.total }
        sortedPayload.each{ userdata ->
            if (userdata.total > 0){
                sanitizedOutput << userdata
            }
        }
        // Create JSON Object
        def list = []
        for (int i = 0; i < 5; i++){
            def jsonMap = [:]
            sortedPayload[i].eachWithIndex{ mapData, index ->
                if (index == 0) {
                    jsonMap["username"] = mapData.value
                } else {
                    def month = "month" + index
                    jsonMap[month] = mapData.value
                }
            }
            list << jsonMap
        }
        def json = JsonOutput.toJson(list)

        Map<String,Object> data = [months: monthPayload, users: usersPayload, output: sanitizedOutput, userJson: json ]
        ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
        morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
	}

	// https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
	// The description associated with the custom report
	 @Override
	 String getDescription() {
		 return "View a history of the user provisioning"
	 }

	// The category of the custom report
	 @Override
	 String getCategory() {
		 return 'inventory'
	 }

	 @Override
	 Boolean getOwnerOnly() {
		 return false
	 }

	 @Override
	 Boolean getMasterOnly() {
		 return true
	 }

	 @Override
	 Boolean getSupportsAllZoneTypes() {
		 return true
	 }

	 @Override
	 List<OptionType> getOptionTypes() {}
 }