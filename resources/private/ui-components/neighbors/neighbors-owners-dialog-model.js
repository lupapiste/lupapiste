LUPAPISTE.NeighborsOwnersDialogModel = function(params) {
  "use strict";
  var self = this;

  self.status = ko.observable();
  self.statusInit                   = 0;
  self.statusSearchPropertyId       = 1;
  self.statusSearchOwners           = 2;
  self.statusSelectOwners           = 3;
  self.statusOwnersSearchFailed     = 4;
  self.statusPropertyIdSearchFailed = 5;

  self.ownersGroups = ko.observable();
  //self.owners = ko.pureComputed(function() {return });
  // _.values(self.ownersByPropertyId());

  self.propertyIds = ko.observable(null);


  self.isSubmitEnabled = ko.pureComputed(function() {
    return self.status() === self.statusSelectOwners;// && self.ownersGroup();
  });

  function convertOwner(owner) {
    var type = owner.henkilolaji,
    nameOfDeceased = null;

    if (owner.yhteyshenkilo) {
      nameOfDeceased = getPersonName(owner);
      owner = owner.yhteyshenkilo;
      type = "kuolinpesan_yhthl";
    }
    return {
      propertyId: owner.propertyId,
      name: getPersonName(owner),
      type: type,
      nameOfDeceased: nameOfDeceased || null,
      businessID: owner.ytunnus || null,
      street: owner.jakeluosoite || null,
      city: owner.paikkakunta || null,
      zip: owner.postinumero || null,
      selected: ko.observable(true)
    };
  }

  self.init = function() {
    return self.status(self.statusInit).propertyIds(null).ownersGroups([]);
  };

  self.isSearching = function() {
    return self.status() === self.statusSearchPropertyId || self.status() === self.statusSearchOwners;
  };

  self.isPropertyIdAvailable = function() {
    return self.propertyIds() !== null;
  };

  self.search = function(x, y) {
    return self.status(self.statusSearchPropertyId).beginUpdateRequest().searchPropertyId(x, y);
  };

  self.searchPropertyId = function(wkt, radius) {
    locationSearch.propertyIdsByWKT(self.requestContext, wkt, radius, self.propertyIdFound, self.propertyIfNotFound);
    return self;
  };

  self.propertyIdFound = function(resp) {
    var propertyIds = _.isArray(resp) && resp.length > 0 ? _.pluck(resp, "kiinttunnus") : null;
    if (propertyIds) {
      return self.propertyIds(propertyIds).status(self.statusSearchOwners).beginUpdateRequest().searchOwners(propertyIds);
    } else {
      return self.propertyIfNotFound();
    }
  };

  self.searchOwners = function(propertyIds) {
    locationSearch.ownersByPropertyIds(self.requestContext, propertyIds, self.ownersFound, self.ownersNotFound);
  };

  self.ownersFound = function(data) {
    var ownersWithObservables = _.map(data.owners, convertOwner);
    var gropupedOwners = _.groupBy(ownersWithObservables, "propertyId");
    var groupsWithSelectAll = _.mapValues(gropupedOwners, function(n) {
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

  self.cancelSearch = function() {
    self.status(self.statusEdit).requestContext.begin();
    return self;
  };

  self.openOwners = function() {
    LUPAPISTE.ModalDialog.open("#dialog-select-owners");
    return self;
  };

  self.addSelectedOwners = function() {
    var selected = _(self.ownersGroups()).pluck("owners").flatten()
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

  // Helper functions
  function getPersonName(person) {
    if (person.sukunimi && person.etunimet) {
      return person.sukunimi + ", " + person.etunimet;
    } else {
      return person.nimi;
    }
  }


  self.init().search(params.wkt, params.radius);
};
