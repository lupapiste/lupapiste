LUPAPISTE.BackendIdManagerModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.verdicts = params.verdicts;
  self.auth = params.authModel;
  self.backendIds = ko.observableArray();
  self.loading = ko.observable(false);
  var initialized = false;

  var validDate = function(d) {
    return _.isDate(d) && d <= (new Date());
  };

  var initialize = function(verdicts) {
    if (_.isArray(verdicts)) {
      var filteredVerdicts = _.filter(verdicts, "kuntalupatunnus");
      self.backendIds(_.map(filteredVerdicts,
        function(verdict) {
          var verdictDate = util.getIn(verdict, ["paatokset", 0, "poytakirjat", 0, "paatospvm"]);
          var verdictDateValue = _.isUndefined(verdictDate) ? undefined : new Date(verdictDate);
          return ko.observable({id: verdict.id,
                                kuntalupatunnus: ko.observable(verdict.kuntalupatunnus),
                                verdictDate: ko.observable(verdictDateValue),
                                invalidDate: ko.observable(!validDate(verdictDateValue))});
        }));
      initialized = true;
    }
  };

  self.verdicts.subscribe(function(verdicts) {
      initialize(verdicts);
  });

  initialize(self.verdicts());

  self.addBackendId = function() {
    self.backendIds.push(ko.observable({id: null, kuntalupatunnus: ko.observable(""), verdictDate: ko.observable(""), invalidDate: ko.observable(false)}));
  };

  self.deleteBackendId = function(index) {
    self.backendIds.splice(index, 1);
  };

  var allIdsValid = function(backendIds) {
    return _.every(backendIds, function(val) {
      return _.trim(val().kuntalupatunnus()).length > 0;
    });
  };

  self.allInputsDisabled = self.disposedPureComputed(function() {
    return self.loading() || !self.auth.ok("store-archival-project-backend-ids");
  });

  self.addButtonDisabled = self.disposedPureComputed(function() {
    return !allIdsValid(self.backendIds()) || self.allInputsDisabled();
  });

  var verdictsModified = function() {
    return self.backendIds().length !== self.verdicts().length ||
      !_.every(self.backendIds(), function(bi) {
        return _.some(self.verdicts(), function(v) {
          var backendId = ko.mapping.toJS(bi);
          var vVerdictDate = util.getIn(v, ["paatokset", 0, "poytakirjat", 0, "paatospvm"]);
          var bVerdictDate = _.isDate(backendId.verdictDate) ? backendId.verdictDate.getTime() : undefined;
          return v.id === backendId.id && v.kuntalupatunnus === backendId.kuntalupatunnus && vVerdictDate === bVerdictDate;
        });
      });
  };

  function verdictArray() {
    return _.map(self.backendIds(), function (bi) {
      var bDate = bi().verdictDate();
      var verdictDate = _.isDate( bDate ) ? bDate.getTime() : null;
      return {id: bi().id, kuntalupatunnus: bi().kuntalupatunnus(), verdictDate: verdictDate};
    });
  }

  self.disposedComputed(function() {
    return ko.toJSON(self.backendIds);
  }).subscribe(function(newValue) {
    if (newValue && initialized && verdictsModified() && allIdsValid(self.backendIds())) {
      var updateVerdict = verdictArray();
      self.loading(true);
      ajax
        .command("store-archival-project-backend-ids",
          {
            id: ko.unwrap(params.applicationId),
            verdicts: ko.mapping.toJS(updateVerdict)
          })
        .success(function () {
          hub.send("indicator", {style: "positive"});
          repository.load(ko.unwrap(params.applicationId));
          initialized = false;
          self.loading(false);
        })
        .error(function (e) {
          hub.send("indicator", {style: "negative", message: e.text});
          self.loading(false);
        })
        .call();
    }
  });
};
