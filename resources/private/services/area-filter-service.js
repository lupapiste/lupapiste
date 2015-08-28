LUPAPISTE.AreaFilterService = function() {
  "use strict";
  var self = this;

  self.data = ko.observable();

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.first(lupapisteApp.models.currentUser.applicationFilters());
    return applicationFilters &&
           applicationFilters.filter.areas &&
           applicationFilters.filter.areas() ||
           [];
  });

  ko.computed(function() {
    ko.utils.arrayPushAll(self.selected,
      _(self.data())
        .map("areas")
        .pluck("features")
        .flatten()
        .filter(function(feature) {
          return _.contains(defaultFilter(), feature.id);
        })
        .map(function(feature) {
          return {id: feature.id, label: util.getFeatureName(feature)};
        })
        .value());
  });

  ajax
    .query("get-organization-areas")
    .error(_.noop)
    .success(function(res) {
      self.data(res.areas);
    })
    .call();
};
