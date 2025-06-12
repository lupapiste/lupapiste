LUPAPISTE.BulletinsSearchModel = function(params) {
  "use strict";
  var self = this;

  self.municipalities = params.municipalities;
  self.states = params.states;
  self.categories = params.categories;

  self.searchText = ko.observable();
  self.municipality = ko.observable();
  self.state = ko.observable();
  self.category = ko.observable();

  hub.send("bulletinService::fetchMunicipalities");

  hub.send("bulletinService::fetchStates");

  hub.send("bulletinService::fetchCategories");

  ko.computed(function() {
    hub.send("bulletinService::searchTermsChanged", {
      searchText: util.getIn(self, ["searchText"], ""),
      municipality: util.getIn(self, ["municipality", "id"], ""),
      state: util.getIn(self, ["state", "id"], ""),
      category: util.getIn(self, ["category", "id"], "")
    });
  });
};
