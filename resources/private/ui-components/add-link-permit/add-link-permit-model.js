LUPAPISTE.AddLinkPermitModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var lpRegex = /^\s*LP-\d{3}-\d{4}-\d{5}\s*$/;

  self.permitTypeIstKT = ko.observable("KT" === lupapisteApp.models.application.permitType());
  self.prefix = ko.observable(self.permitTypeIstKT() ? "kt." : "");
  self.appId = lupapisteApp.services.contextService.applicationId;
  self.propertyId = ko.observable("");
  self.kuntalupatunnus = ko.observable("");
  self.selectedLinkPermit = ko.observable("");
  self.appMatches = ko.observableArray([]);
  self.errorMessage = ko.observable(null);
  self.processing = ko.observable();
  self.pending = ko.observable();

  self.ok = self.disposedComputed(function() {
    // XOR in javascript
    var onlyOtherSelected = (self.kuntalupatunnus() || self.selectedLinkPermit()) &&
                            !(self.kuntalupatunnus() && self.selectedLinkPermit());

    return onlyOtherSelected && !lpRegex.test(self.kuntalupatunnus());
  });

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  var getAppMatchesForLinkPermitsSelect = function(appId) {
    ajax.query("app-matches-for-link-permits", {id: appId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function(data) {
        self.errorMessage(null);
        self.appMatches(data["app-links"]);
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.disposedComputed(function() {
    self.propertyId(lupapisteApp.models.application.propertyId());
    self.selectedLinkPermit("");
    self.kuntalupatunnus("");
    self.errorMessage(null);
    self.processing(false);
    self.pending(false);

    getAppMatchesForLinkPermitsSelect(self.appId());
  });

  self.onSuccess = function() {
    self.errorMessage(null);
    repository.load(self.appId());
    self.back();
  };

  self.addLinkPermit = function() {
    var lupatunnus = util.getIn(self.selectedLinkPermit, ["id"]) || self.kuntalupatunnus();
    var params = {id: self.appId(), linkPermitId: lupatunnus};
    ajax.command("add-link-permit", params)
      .processing(self.processing)
      .pending(self.pending)
      .success(self.onSuccess)
      .onError("error.max-incoming-link-permits", function(originalErrorResp) {
        // Retry with inverted params
        var invertedParams = {id: lupatunnus, linkPermitId: self.appId};
        ajax.command("add-link-permit", invertedParams)
          .processing(self.processing)
          .pending(self.pending)
          .success(self.onSuccess)
          .error(function() {
            self.onError(originalErrorResp);
          })
          .call();
      })
      .error(self.onError)
      .call();
  };

  self.back = function() {
    hub.send(  "cardService::select", {deck: "summary",
                                       card: "info"});
  };
};
