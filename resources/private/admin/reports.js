(function() {
  "use strict";

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
