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
  self.applicationsEnabled = ko.observable(false);
  self.infoRequests = ko.observable(true);
  self.openInfoRequests = ko.observable(false);
  self.openInfoRequestEmail = ko.observable();


};
