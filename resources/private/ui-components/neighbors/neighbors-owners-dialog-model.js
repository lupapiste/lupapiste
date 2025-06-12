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

  function convertOwner(getPropertyIdSelectedByDefault, owner) {
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
      businessID: person.ytunnus,
      street: person.jakeluosoite,
      city: person.paikkakunta,
      zip: person.postinumero,
      selected: ko.observable(getPropertyIdSelectedByDefault(owner.propertyId))
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
    var propertyIds = _.isArray(resp) && resp.length > 0 ? _.map(resp, "kiinttunnus") : null;
    if (propertyIds) {
        return self.propertyIds(propertyIds)
            .status(self.statusSearchOwners)
            .beginUpdateRequest()
            .searchOwnersByPropertyIds(propertyIds);
    } else {
      return self.propertyIfNotFound();
    }
  };

  self.searchOwnersByPropertyIds = function(propertyIds) {
    var applicationPropertyId = lupapisteApp.models.application.propertyId();
    self.emptyPropertyIds([]);
    self.searchRequests.push(locationSearch.ownersByPropertyIds(self.requestContext,
                                                                propertyIds,
                                                                _.partialRight(self.ownersFound,
                                                                               function (propertyId) {
                                                                                   return propertyId !== applicationPropertyId;
                                                                               },
                                                                               propertyIds),
                                                                _.partial(self.ownersNotFound, propertyIds)));
    return self;
  };

  self.searchOwnersByApplicationId = function(applicationId) {
    self.searchRequests.push(ajax.query("application-property-owners", {id:applicationId})
                             .success(_.partialRight(self.ownersFound, _.stubTrue))
                             .error(self.ownersNotFound)
                             .call());
    return self;
  };

  self.ownersFound = function(data, getPropertyIdSelectedByDefault, allPropertyIds) {
    var ownersWithObservables = _.map(data.owners, _.partial(convertOwner, getPropertyIdSelectedByDefault));
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

  self.ownersNotFound = function( allPropertyIds ) {
    self.emptyPropertyIds( allPropertyIds );
    return self.status(self.statusOwnersSearchFailed);
  };

  self.showManualAdd = function() {
    // The same dialog is shown also in non-neighbour
    // scenarios. Readonly flag denotes non-neighbor use case.
    return (_.isEmpty( self.ownersGroups() )
            || _.some(self.emptyPropertyIds()))
      && !self.isSearching()
      && !self.readonly;
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
