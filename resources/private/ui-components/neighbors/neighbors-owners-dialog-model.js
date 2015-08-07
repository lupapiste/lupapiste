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

  self.owners = ko.observableArray();
  self.propertyId = ko.observable();

  self.ownersGroup = ko.computed({
    read: function() {
      var someSelected = _.find(self.owners(), function(owner) {
        return owner.selected();
      });
      return someSelected !== undefined;
    },
    write: function(state) {
      self.owners().forEach(function(owner) {
        owner.selected(state);
      });
    }
  });

  self.isSubmitEnabled = ko.pureComputed(function() {
    return self.status() === self.statusSelectOwners && self.ownersGroup();
  });

  self.init = function() {
    return self.status(self.statusInit).propertyId(null).owners([]);
  };

  self.isSearching = function() {
    return self.status() === self.statusSearchPropertyId || self.status() === self.statusSearchOwners;
  };

  self.isPropertyIdAvailable = function() {
    return self.propertyId() !== null;
  };

  self.search = function(x, y) {
    return self.status(self.statusSearchPropertyId).beginUpdateRequest().searchPropertyId(x, y);
  };

  self.searchPropertyId = function(x, y) {
    locationSearch.propertyIdByPoint(self.requestContext, x, y, self.propertyIdFound, self.propertyIfNotFound);
    return self;
  };

  self.propertyIdFound = function(propertyId) {
    if (propertyId) {
      return self.propertyId(propertyId).status(self.statusSearchOwners).beginUpdateRequest().searchOwners(propertyId);
    } else {
      return self.propertyIfNotFound();
    }
  };

  self.searchOwners = function(propertyId) {
    locationSearch.ownersByPropertyId(self.requestContext, propertyId, self.ownersFound, self.ownersNotFound);
  };

  self.ownersFound = function(data) {
    return self.owners(_.map(data.owners, convertOwner)).status(self.statusSelectOwners);
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
    var selected = _.filter(self.owners(), function(owner) {
      return  owner.selected();
    });
    var applicationId = lupapisteApp.models.application.id();
    var parameters = {
      id: applicationId,
      propertyId: self.propertyId(),
      owners: selected
    };

    ajax
      .command("neighbor-add-owners", parameters)
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
  function convertOwner(owner) {
    var type = owner.henkilolaji,
    nameOfDeceased = null;

    if (owner.yhteyshenkilo) {
      nameOfDeceased = getPersonName(owner);
      owner = owner.yhteyshenkilo;
      type = "kuolinpesan_yhthl";
    }
    return {
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

  self.init().search(params.x, params.y);
};
