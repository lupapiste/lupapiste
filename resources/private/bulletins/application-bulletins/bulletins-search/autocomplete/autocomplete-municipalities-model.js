LUPAPISTE.AutocompleteMunicipalitiesModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selected;
  self.query = params.query;
  self.queryString = ko.observable("");

  self.data = ko.pureComputed(function() {
    // TODO refactor clearSelected item into autocomplete
    var all = {id: "all", label: loc("bulletin.municipalities-search.caption"), behaviour: "clearSelected"};
    var municipalityData = _.map(params.municipalities(), function(m) {
      return {id: m, label: loc(["municipality", m])};
    });
    return _(util.filterDataByQuery({data: municipalityData,
                                     query: self.queryString(),
                                     selected: self.selected()}))
      .sortBy("label")
      .unshift(all)
      .value();
  });
};
