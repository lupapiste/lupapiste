LUPAPISTE.AreaFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  var _data = ko.observable();

  _data.subscribe( function ()  {
    hub.send( "areaFilterService::changed", {} );
  });

  self.data = ko.pureComputed(function() {
    return _data();
  });

  self.selected = ko.observableArray([]);

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "areas"]);
  });

  ko.computed(function() {
    self.selected([]);
    ko.utils.arrayPushAll(self.selected,
      _(self.data())
        .map("areas")
        .map("features")
        .flatten()
        .filter(function(feature) {
          if (savedFilter()) {
            return  _.includes(savedFilter(), feature.id);
          }
        })
        .map(function(feature) {
          return {id: feature.id, label: util.getFeatureName(feature)};
        })
        .value());
  });

  function load() {
    if (lupapisteApp.models.globalAuthModel.ok("get-organization-areas")) {
      ajax.query("get-organization-areas")
        .success(function(res) {
          _data(res.areas);
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }

};
