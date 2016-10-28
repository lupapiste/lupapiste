LUPAPISTE.NeighborsOwnersDialogModel = function(params) {
  "use strict";
  var self = this;
  self.searchRequests = [];

  self.statusInit                   = 0;
  self.statusSearchPropertyId       = 1;
  self.statusSearchOwners           = 2;
  self.statusSelectOwners           = 3;
  self.statusOwnersSearchFailed     = 4;
  self.statusPropertyIdSearchFailed = 5;
  self.readonly = false;

  self.status = ko.observable(self.statusInit);
  self.ownersGroups = ko.observable([]);
  self.propertyIds = ko.observable(null);
  self.emptyPropertyIds = ko.observableArray();

  self.isSubmitEnabled = ko.pureComputed(function() {
    return !self.readonly && self.status() === self.statusSelectOwners
           && _.some(self.ownersGroups(), function(o) {return o.ownersGroup();} );
  });

  // Helper functions
  function getPersonName(person) {
    return person.sukunimi && person.etunimet ?
        person.sukunimi + ", " + person.etunimet : person.nimi;
  }
  function convertOwner(owner) {
    var person = owner;
    var type = owner.henkilolaji;
    var nameOfDeceased = null;

    if (owner.yhteyshenkilo) {
      person = owner.yhteyshenkilo;
      nameOfDeceased = getPersonName(owner);
      type = "kuolinpesan_yhthl";
    }

    return {
      propertyId: owner.propertyId,
      name: getPersonName(person),
      type: type,
      nameOfDeceased: nameOfDeceased,
      businessID: person.ytunnus || null,
      street: person.jakeluosoite || null,
      city: person.paikkakunta || null,
      zip: person.postinumero || null,
      selected: ko.observable(true)
    };
  }

  self.isSearching = ko.pureComputed(function() {
    return self.status() === self.statusSearchPropertyId || self.status() === self.statusSearchOwners;
  });

  self.searchLocation = function(wkt, radius) {
    self.status(self.statusSearchPropertyId).beginUpdateRequest();
    self.searchRequests.push(locationSearch.propertyIdsByWKT(self.requestContext, wkt, radius, self.propertyIdFound, self.propertyIfNotFound));
    return self;
  };

  self.propertyIdFound = function(resp) {
    var applicationPropertyId = lupapisteApp.models.application.propertyId();
    var propertyIds = _.isArray(resp) && resp.length > 0 ? _.map(resp, "kiinttunnus") : null;
    if (propertyIds) {
      var filteredPropertyIds = _.filter(propertyIds, function(p) {return p !== applicationPropertyId;});
      return self.propertyIds(filteredPropertyIds).status(self.statusSearchOwners).beginUpdateRequest().searchOwnersByPropertyIds(filteredPropertyIds);
    } else {
      return self.propertyIfNotFound();
    }
  };

  self.searchOwnersByPropertyIds = function(propertyIds) {
    self.emptyPropertyIds([]);
    self.searchRequests.push(locationSearch.ownersByPropertyIds(self.requestContext,
                                                                propertyIds,
                                                                _.partialRight(self.ownersFound, propertyIds),
                                                                self.ownersNotFound));
    return self;
  };

  self.searchOwnersByApplicationId = function(applicationId) {
    self.searchRequests.push(ajax.query("application-property-owners", {id:applicationId})
                                .success(self.ownersFound).error(self.ownersNotFound).call());
    return self;
  };

  self.ownersFound = function(data, allPropertyIds ) {
    var ownersWithObservables = _.map(data.owners, convertOwner);
    var groupedOwners = _.groupBy(ownersWithObservables, "propertyId");
    var groupsWithSelectAll = _.mapValues(groupedOwners, function(n) {
      var ownersGroup = ko.computed({
        read: function() {
          return _.some(n, function(owner) {
            return owner.selected();
          });
        },
        write: function(state) {
          _.forEach(n, function(owner) {
            owner.selected(state);
          });
        }
      });
      self.emptyPropertyIds(_.difference( allPropertyIds, _.keys( groupedOwners ) ));
      return {owners: n, ownersGroup: ownersGroup};
    });
    return self.ownersGroups(_.values(groupsWithSelectAll)).status(self.statusSelectOwners);
  };



  self.propertyIfNotFound = function() {
    return self.status(self.statusPropertyIdSearchFailed);
  };

  self.ownersNotFound = function() {
    return self.status(self.statusOwnersSearchFailed);
  };

  self.addSelectedOwners = function() {
    var selected = _(self.ownersGroups()).map("owners").flatten()
                       .filter(function(o) {return o.selected();}).value();

    var applicationId = lupapisteApp.models.application.id();

    ajax.command("neighbor-add-owners", {id: applicationId, owners: selected})
      .success(function() {
        repository.load(applicationId);
        hub.send("close-dialog");
      })
      .call();
    return self;
  };

  self.beginUpdateRequest = function() {
    self.requestContext.begin();
    return self;
  };
  self.requestContext = new RequestContext();

  // Abort every search request when the dialog is closed
  self.dispose = function() {
    _.each(self.searchRequests, function(r) {
      r.abort();
    });
  };

  // Init
  if (params.wkt) {
    self.searchLocation(params.wkt, params.radius);
  } else if (params.applicationId) {
    self.readonly = true;
    self.status(self.statusSearchOwners).searchOwnersByApplicationId(params.applicationId);
  } else {
    error("Not enough params for NeighborsOwnersDialogModel");
  }
};
