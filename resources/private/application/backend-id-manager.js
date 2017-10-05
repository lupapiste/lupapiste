LUPAPISTE.BackendIdManagerModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.verdicts = params.verdicts;
  self.auth = params.authModel;
  self.backendIds = ko.observableArray();
  self.mainVerdictDate = ko.observable();
  self.loading = ko.observable(false);
  var initialized = false;
  var initialVerdictsTs = null;

  var initialize = function(verdicts) {
    if (_.isArray(verdicts)) {
      var filteredVerdicts = _.filter(verdicts, "kuntalupatunnus");
      self.backendIds(_.map(filteredVerdicts,
        function(verdict) {
          return ko.observable({id: verdict.id, kuntalupatunnus: ko.observable(verdict.kuntalupatunnus)});
        }));
      initialVerdictsTs = util.getIn(filteredVerdicts, [0, "paatokset", 0, "poytakirjat", 0, "paatospvm"]);
      if (initialVerdictsTs) {
        self.mainVerdictDate(new Date(initialVerdictsTs));
      }
      initialized = true;
    }
  };

  self.verdicts.subscribe(function(verdicts) {
    initialize(verdicts);
  });

  initialize(self.verdicts());

  self.addBackendId = function() {
    self.backendIds.push(ko.observable({id: null, kuntalupatunnus: ko.observable("")}));
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
        return v.id === backendId.id && v.kuntalupatunnus === backendId.kuntalupatunnus;
        });
      });
  };

  self.disposedComputed(function() {
    return ko.toJSON(self.backendIds);
  }).subscribe(function(newValue) {
    if (newValue && initialized && verdictsModified() && allIdsValid(self.backendIds())) {
      self.loading(true);
      ajax
        .command("store-archival-project-backend-ids",
          {
            id: ko.unwrap(params.applicationId),
            verdicts: ko.mapping.toJS(self.backendIds)
          })
        .success(function () {
          hub.send("indicator", {style: "positive"});
          repository.load(ko.unwrap(params.applicationId));
          self.loading(false);
        })
        .error(function (e) {
          hub.send("indicator", {style: "negative", message: e.text});
          self.loading(false);
        })
        .call();
    }
  });

  var validDate = function(d) {
    return _.isDate(d) && d <= (new Date());
  };

  self.invalidDate = self.disposedPureComputed(function() {
    return !validDate(self.mainVerdictDate());
  });

  self.disposedComputed(function() {
    var d = self.mainVerdictDate();
    if (validDate(d) && d.getTime() !== initialVerdictsTs) {
      ajax
        .command("set-archival-project-permit-date", {
          id: ko.unwrap(params.applicationId),
          date: d.getTime()
        })
        .success(function () {
          hub.send("indicator", {style: "positive"});
          initialVerdictsTs = d.getTime();
        })
        .error(function (e) {
          hub.send("indicator", {style: "negative", message: e.text});
          repository.load(ko.unwrap(params.applicationId));
        })
        .call();
    }
  });
};
