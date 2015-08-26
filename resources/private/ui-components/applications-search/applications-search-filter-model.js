LUPAPISTE.OperationsDataProvider = function(filtered) {
  "use strict";
  var self = this;

  var operationsByPermitType = ko.observable();

  self.query = ko.observable();

  self.filtered = filtered || ko.observableArray([]);

  ajax
    .query("get-application-operations")
    .error(_.noop)
    .success(function(res) {
      operationsByPermitType(res.operationsByPermitType);
    })
    .call();

  function wrapInObject(operations) {
    return _.map(operations, function(op) {
      return {label: loc("operations." + op),
              id: op};
    });
  }

  self.data = ko.pureComputed(function() {
    var result = [];

    var data = _.map(operationsByPermitType(), function(operations, permitType) {
      return {
        permitType: loc(permitType),
        operations: wrapInObject(operations)
      };
    });


    _.forEach(data, function(item) {
      var header = {label: item.permitType, groupHeader: true};

      var filteredData = util.filterDataByQuery(item.operations, self.query() || "", self.filtered());

      // append group header and group items to result data
      if (filteredData.length > 0) {
        result = result.concat(header).concat(filteredData);
      }
    });

    return result;
  });

};

LUPAPISTE.HandlersDataProvider = function() {
  "use strict";
  var self = this;

  self.query = ko.observable();

  var data = ko.observable();

  function mapUser(user) {
    var fullName = "";
    if (user) {
      if (user.lastName) { fullName += user.lastName; }
      if (user.firstName && user.lastName) { fullName += "\u00a0"; }
      if (user.firstName) { fullName +=  user.firstName; }
    }
    user.fullName = fullName;
    return user;
  }

  ajax
    .query("users-in-same-organizations")
    .error(_.noop)
    .success(function(res) {
      data(_(res.users).map(mapUser).sortBy("fullName").value());
    })
    .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(data(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.fullName.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
  });
};

LUPAPISTE.AreasDataProvider = function(filtered) {
  "use strict";

  var self = this;

  self.query = ko.observable();
  self.filtered = filtered || ko.observableArray([]);

  var orgsAreas = ko.observable();

  ajax
    .query("get-organization-areas")
    .error(_.noop)
    .success(function(res) {
      orgsAreas(res.areas);
    })
    .call();

  self.data = ko.computed(function() {
    var result = [];

    _.forEach(orgsAreas(), function(org) {
      if (org.areas && org.areas.features) {
        var header = {label: org.name[loc.currentLanguage], groupHeader: true};


        var features = _.map(org.areas.features, function(feature) {
          for(var key in feature.properties) { // properties to lower case
            feature.properties[key.toLowerCase()] = feature.properties[key];
          }
          var nimi = util.getIn(feature, ["properties", "nimi"]);
          var id = feature.id;
          return {label: nimi, id: id};
        });
        features = _.filter(features, "label");

        var filteredData = util.filterDataByQuery(features, self.query() || "", self.filtered());
        // append group header and group items to result data
        if (filteredData.length > 0) {
          if (_.keys(orgsAreas()).length > 1) {
            result = result.concat(header);
          }
          result = result.concat(filteredData);
        }
      }
    });

    return result;
  });
};

LUPAPISTE.OrganizationsDataProvider = function(savedOrgFilters) {
  "use strict";

  var self = this;

  self.query = ko.observable();
  self.savedOrgFilters = savedOrgFilters || ko.observableArray([]);

  var data = ko.observable();
  var usersOwnOrganizations = _.keys(lupapisteApp.models.currentUser.orgAuthz());

  ajax
  .query("get-organization-names")
  .error(_.noop)
  .success(function(res) {
    var ownOrgsWithNames = _.map(usersOwnOrganizations, function(org) {
      return {id: org, name: res.names[org][loc.currentLanguage]};
    });
    data(ownOrgsWithNames);
  })
  .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(data(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.name.toUpperCase(), word.toUpperCase()) &&
          !_.some(self.savedOrgFilters(), item) && result;
      }, true);
    });
  });

};

LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.handlersDataProvider = null;
  self.areasDataProvider = null;

  self.showAdvancedFilters = ko.observable(false);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  self.saveAdvancedFilters = function() {
    var filter = {
      tags:          _.map(ko.unwrap(self.dataProvider.tags), "id"),
      operations:    _.map(ko.unwrap(self.dataProvider.operations), "id"),
      organizations: _.map(ko.unwrap(self.dataProvider.organizations), "id"),
      areas:         _.map(ko.unwrap(self.dataProvider.areas), "id")
    };

    ajax
    .command("update-default-application-filter", {filter: filter})
    .error(_.noop)
    .success(function() {
      // TODO show indicator for success
    })
    .call();
  };

  if ( lupapisteApp.models.currentUser.isAuthority() ) {
    self.handlersDataProvider = new LUPAPISTE.HandlersDataProvider();
    // self.operationsDataProvider = new LUPAPISTE.OperationsDataProvider(self.dataProvider.operations);
    self.organizationsDataProvider = new LUPAPISTE.OrganizationsDataProvider(self.dataProvider.organizations);
    // self.tagsDataProvider = new LUPAPISTE.TagsDataProvider(self.dataProvider.tags);
    self.areasDataProvider = new LUPAPISTE.AreasDataProvider(self.dataProvider.areas);
  }
};
