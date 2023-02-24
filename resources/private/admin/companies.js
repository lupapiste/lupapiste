;(function() {
  "use strict";

  function getUserLimit(c) {
    if (c.accountType === "custom") {
      return c.customAccountLimit ? c.customAccountLimit : 0;
    } else {
      return _.find(LUPAPISTE.config.accountTypes, {name: c.accountType}).limit;
    }
  }

  function CompaniesModel() {
    var self = this;

    self.companies = ko.observableArray([]);
    self.pending = ko.observable();

    self.editDialog = function(company) {
     hub.send("show-dialog", {title: "Muokkaa yrityst\u00e4",
                              size: "medium",
                              component: "company-edit",
                              componentParams: {company: company}});
    };

    self.load = function() {
      ajax
        .query("companies")
        .pending(self.pending)
        .success(function(d) {
          self.companies(_(d.companies).map(function(c) {
            return _.assign(c, {userLimit: getUserLimit(c),
                                openDialog: self.editDialog});
          }).sortBy("name").value());
        })
        .call();
    };
    self.unlock = function( company ) {
      companyLock( company.id, "unlock" );
    };
    self.lock = function( company ) {
      var dateObs = ko.observable( util.finnishDate( company.locked ));
      hub.send( "show-dialog", {component: "date-editor",
                                componentParams: {date: dateObs,
                                                  okFn: function() {
                                                    companyLock( company.id,
                                                                 util.toMoment( dateObs(), "fi").valueOf());
                                                  }},
                                title: "Yrityksen sulkeminen",
                                size: "medium"});
    };
  }

  var companiesModel = new CompaniesModel();

  function companyLock( companyId, timestamp ) {
    ajax.command( "company-lock", {company: companyId,
                                  timestamp: timestamp })
    .success( companiesModel.load )
    .call();
  }



  hub.subscribe("company-created", companiesModel.load);
  hub.subscribe("company-updated", companiesModel.load);
  hub.onPageLoad("companies", companiesModel.load);

  $(function() {
    $("#companies").applyBindings({
      companiesModel: companiesModel
    });
  });

})();
