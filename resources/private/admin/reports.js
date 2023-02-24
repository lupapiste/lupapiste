(function() {
  "use strict";

  // Legacy code gonna legacy:
  /* globals self: true */

  // Helper function for DRYing up the timestamp declarations
  self.timestampComputed = function(dateObservable, isEnd) {
    return ko.pureComputed(function() {
      var date = moment(dateObservable());
      var time = isEnd ? date.endOf("day") : date.startOf("day");
      return time.valueOf();});};

  self.startOfMonth = moment().startOf("month").toDate();

  // Report time observables
  self.lastMonthStart = ko.observable(moment().subtract(1, "months").startOf("month").startOf("day").valueOf());
  self.lastMonthEnd   = ko.observable(moment().subtract(1, "months").endOf("month").endOf("day").valueOf());

  //Report for billing time observables
  self.startDate = ko.observable(self.startOfMonth);
  self.endDate   = ko.observable(new Date());

  self.startComputed = self.timestampComputed(self.startDate, false);
  self.endComputed   = self.timestampComputed(self.endDate, true);

  //Report for downloads time observables
  self.startDateForDownloads = ko.observable(self.startOfMonth);
  self.endDateForDownloads   = ko.observable(new Date());

  self.startForDownloadsComputed = self.timestampComputed(self.startDateForDownloads, false);
  self.endForDownloadsComputed   = self.timestampComputed(self.endDateForDownloads, true);

  //Report for digitizing time observables
  self.startDateForDigitizing = ko.observable(self.startOfMonth);
  self.endDateForDigitizing   = ko.observable(new Date());

  self.startForDigitizingComputed = self.timestampComputed(self.startDateForDigitizing, false);
  self.endForDigitizingComputed   = self.timestampComputed(self.endDateForDigitizing, true);

  // Rami logins report observables
  self.startRamiDate = ko.observable(self.startOfMonth);
  self.endRamiDate   = ko.observable(new Date());

  self.startRamiTs = self.timestampComputed(self.startRamiDate, false);
  self.endRamiTs   = self.timestampComputed(self.endRamiDate, true);

  // Verdicts/contracts report observables
  self.startVerdictDate = ko.observable(self.startOfMonth);
  self.endVerdictDate   = ko.observable(new Date());

  self.startVerdictTs = self.timestampComputed(self.startVerdictDate, false);
  self.endVerdictTs   = self.timestampComputed(self.endVerdictDate, true);


  function UserReport() {
    var self = this;

    self.options = [{value: "yes", text: "Kyll\u00e4"},
                    {value: "no", text: "Ei"},
                    {value: "both", text: "Sek\u00e4 ett\u00e4"}];
    self.values = [{value: ko.observable(), label: "Yritystili",
                    arg: "company"},
                   {value: ko.observable(), label: "Ammattilainen",
                    arg: "professional"},
                   {value: ko.observable(), label: "Suoramarkkinointilupa",
                    arg: "allow"}];
    self.emails = ko.observable();

    self.link = ko.pureComputed( function() {
      return "/api/raw/user-report?"
           + _(self.values)
             .map( function( v ) {
               return sprintf( "%s=%s", v.arg, v.value());
             })
             .join( "&");
    });

    ajax.query( "company-unsubscribed-emails")
    .success( function( res ) {
      self.emails( _.join( res.emails, "\n"));
    })
    .call();

    self.upsert = function() {
      ajax.command( "upsert-company-unsubscribed", {emails: self.emails()})
      .success( util.showSavedIndicator )
      .error( util.showSavedIndicator)
      .call();
    };
  }

  function ApplicationsReport() {
    var self = this;

    self.monthInput = ko.observable(moment().format("M"));
    self.yearInput = ko.observable(moment().format("YYYY"));
    self.monthValue = ko.observable();
    self.yearValue = ko.observable();
    self.results = ko.observableArray();
    self.totalCountApp = ko.observable();
    self.totalCountOp = ko.observable();

    self.reset = function() {
      self.results([]);
      self.totalCountApp();
      self.totalCountOp();
      self.monthValue(self.monthInput());
      self.yearValue(self.yearInput());
    };

    self.fetch = function() {
    self.reset();
      ajax.query("applications-per-month-report", {month: self.monthInput(), year: self.yearInput()})
      .success(function(res) {
        self.results(res.applications);
        self.totalCountApp(_.sumBy(res.applications, "countApp"));
        self.totalCountOp(_.sumBy(res.applications, "countOp"));
      })
      .call();
    };
  }

  $(function() {
    $("#reports").applyBindings({users: new UserReport(),
                                 applications: new ApplicationsReport()});
  });

})();
