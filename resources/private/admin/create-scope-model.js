LUPAPISTE.CreateScopeModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  self.organization = params.organization;
  // Use the first municipapity in scope, or try to guess by organizations id (should it follow the convention)
  var municipalityNumber = util.getIn(params.organization, ["scope", 0, "municipality"], _.first(_.words(self.organization.id(), /-/)));
  var mappedMunicipalities = _(params.municipalities)
                              .map(function(muniId) {
                                return {id: muniId,
                                        label: loc(["municipality", muniId]) + " (" + muniId + ")"};
                              })
                              .sortBy("label")
                              .value();
  self.municipalities = ko.observableArray(mappedMunicipalities);

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
                municipality: self.municipality().id,
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
