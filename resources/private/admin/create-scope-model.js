LUPAPISTE.CreateScopeModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  self.organization = self.params.organization;
  var municipalityNumber = _.first(_.words(self.organization.id(), /-/));
  self.municipalities = ko.observableArray([{id: "297", label: "Kuopio"}, {id: "295", label: "Luopio"}]);

  self.permitTypeLoc = function(data) {
    return data + " - " + loc(data);
  };

  self.permitType = ko.observable("R");
  self.municipality = ko.observable(_.find(self.municipalities(), {id: municipalityNumber}));
  self.applicationEnabled = ko.observable(false);
  self.infoRequests = ko.observable(true);
  self.openInfoRequests = ko.observable(false);
  self.openInfoRequestEmail = ko.observable("");
  self.opening = ko.observable();


  self.saveScope = function() {
    var openingMills = null;
    if (self.opening()) {
      openingMills = new Date(self.opening()).getTime();
    }
    var data = {organization: self.organization.id(),
                permitType: self.permitType(),
                municipality: "297",
                inforequestEnabled: self.infoRequests(),
                applicationEnabled: self.applicationEnabled(),
                openInforequestEnabled: self.openInfoRequests(),
                openInforequestEmail: self.openInfoRequestEmail(),
                opening: openingMills};

    ajax
      .command("add-scope", data)
      .success(function() {
        hub.send("organization::scope-added", {orgId: self.organization.id()});
        hub.send("close-dialog"); // dispose
      })
      .call();
  };


};
