// Service managing assurances for application submission.
LUPAPISTE.ApplicationSubmitAssurancesService = function () {
  "use strict";
  var self = this;

  self.appModel = lupapisteApp.models.application;

  self.loadedAssurancesAppId = null;
  self.assurances = ko.observableArray([]);
  self.selectedAssurances = ko.observableArray([]);

  // assurancesInitialized prevents submission of an application if assurances are still loading
  self.assurancesInitialized = ko.observable(false);

  self.clear = function () {
    self.loadedAssurancesAppId = null;
    self.assurances([]);
    self.selectedAssurances([]);
  };

  self.fetchAssurances = function () {

    var appId = self.appModel.id();

    // Clear assurance if applications is not loaded in appModel or organization does not have assurance feature on
    if (!lupapisteApp.models.applicationAuthModel.ok("application-submit-assurance-templates")
        || !appId
        || !self.appModel.state()) {
      self.clear();
      self.assurancesInitialized(true);
      return;
    }

    if (self.loadedAssurancesAppId !== appId) {
      //  Clear to prevent displaying incorrect assurances during loading.
      self.clear();
    }

    self.assurancesInitialized(false);
    ajax.query("application-submit-assurance-templates", {id: appId})
        .success(function (res) {
          self.assurances(_.isArray(res.assurances) ? res.assurances : []);
          self.loadedAssurancesAppId = appId;
          self.assurancesInitialized(true);
        })
        .error(function () {
          self.clear();
          self.assurancesInitialized(true);
        })
        .call();
  };

  self.getAssuranceObservables = function () {
    self.fetchAssurances();
    return {
      assurances: self.assurances,
      selectedAssurances: self.selectedAssurances
    };
  };

  self.clearSelectedAssurances = function () {
    self.selectedAssurances([]);
  };

  self.requiredAssurancesChecked = ko.computed(function () {
    return self.assurancesInitialized() && self.assurances()
        .every(function (assurance) {
          return !assurance.required || self.selectedAssurances().includes(assurance.id);
        });
  });

  self.selectedAssurancesData = ko.computed(function () {
    return self.assurances()
        .filter(function (assurance) {
          return self.selectedAssurances().includes(assurance.id);
        })
        .map(function (assurance) {
          return {
            id: assurance.id, modified: assurance.modified
          };
        });
  });

  hub.subscribe("contextService::leave", self.clear);
};
