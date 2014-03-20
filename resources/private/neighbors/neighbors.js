(function() {
  "use strict";

  function makeNew(propertyId) {
    return {
      propertyId: propertyId,
      owner: {
        name: ko.observable(""),
        address: {
          street: ko.observable(""),
          city: ko.observable(""),
          zip: ko.observable("")
        }
      },
      state: "new"
    };
  }

  var applicationId;
  var model = new Model();
  var editModel = new EditModel();
  var ownersModel = new OwnersModel();

  function Model() {
    var self = this;

    self.applicationId = ko.observable();
    self.neighbors = ko.observableArray();
    self.neighborId = ko.observable();
    self.map = null;

    self.init = function(application) {
      if (!self.map) self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
      var location = application.location,
          x = location.x,
          y = location.y;
      self
        .applicationId(application.id)
        .neighbors(application.neighbors)
        .neighborId(null)
        .map.updateSize().clear().center(x, y, 13).add(x, y); // original zoom 11
    };

    self.edit   = function(neighbor) {
        editModel.init(neighbor).edit().openEdit();
    };
    self.add    = function() {
        editModel.init().edit().openEdit();
    };
    self.click  = function(x, y) {
        ownersModel.init().search(x, y).openOwners();
    };
    self.done = function() {
        window.location.hash = "!/application/" + applicationId + "/statement";
    };
    self.remove = function(neighbor) {
      self.neighborId(neighbor.neighborId);
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("neighbors.remove-dialog.title"),
        loc("neighbors.remove-dialog.message"),
        {title: loc("yes"), fn: self.removeNeighbor},
        {title: loc("no")}
      );
      return self;
    };

    self.removeNeighbor = function() {
      ajax
        .command("neighbor-remove", {id: self.applicationId(), neighborId: self.neighborId()})
        .complete(_.partial(repository.load, self.applicationId(), util.nop))
        .call();
      return self;
    };
  }

  function OwnersModel() {

      var self = this, allSelectedWatch, selectedOwnersWatch;

      self.status = ko.observable();
      self.statusInit             = 0;
      self.statusSearchPropertyId = 1;
      self.statusSearchOwners     = 2;
      self.statusSelectOwners     = 3;
      self.statusOwnersSearchFailed     = 4;
      self.statusPropertyIdSearchFailed = 5;

      self.owners = ko.observableArray();
      self.propertyId = ko.observable();
      self.selectedOwners = ko.observableArray([]);
      self.allSelected = ko.observable(false).extend({ notify: 'always' });;

      function watchAllSelected() {
          allSelectedWatch = self.allSelected.subscribe(function(allSelected) {
              selectedOwnersWatch.dispose();
              self.selectedOwners.removeAll();
              if (allSelected) {
                  _.each(self.owners(), function(owner, index) { self.selectedOwners.push("" + index); });
              }
              watchSelectedOwners();
          });
      };
      function watchSelectedOwners() {
          selectedOwnersWatch = self.selectedOwners.subscribe(function(newValue) {
              allSelectedWatch.dispose();
              var ownersLength = self.owners().length;
              self.allSelected(ownersLength > 0 && ownersLength === newValue.length);
              watchAllSelected();
          });
      };
      watchAllSelected();
      watchSelectedOwners();

      self.init = function() {
          return self.status(self.statusInit).propertyId(null).owners([]).selectedOwners([]);
      };
      self.isSearching = function() {
          return self.status() === self.statusSearchPropertyId || self.status() === self.statusSearchOwners;
      };
      self.isPropertyIdAvailable = function() {
          return self.propertyId() != null;
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
          return self.owners(_.map(data.owners, convertOwner)).allSelected(true).status(self.statusSelectOwners);
      }

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
          var owners = _.map(self.selectedOwners(), function(indx) { return self.owners()[indx]; }),
              parameters = {
                  id: applicationId,
                  propertyId: self.propertyId(),
                  owners: owners
              };
          ajax
          .command("neighbor-add-owners", parameters)
          .complete(_.partial(repository.load, applicationId,
                  function(v) {
                      LUPAPISTE.ModalDialog.close();
                  }))
          .call();
        return self;
      }

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
      function convertOwner(owner, index) {
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
              zip: owner.postinumero || null
          };
      }
  }

  function EditModel() {
    var self = this;

    self.status = ko.observable();
    self.statusInit         = 0;
    self.statusEdit         = 2;

    self.init = function(data) {
      var data = data || {},
          neighbor = data.neighbor || {},
          owner = neighbor.owner || {},
          address = owner.address || {};
      return self
        .status(self.statusInit)
        .id(applicationId)
        .neighborId(data.neighborId)
        .propertyId(neighbor.propertyId)
        .name(owner.name)
        .street(address.street)
        .city(address.city)
        .zip(address.zip)
        .email(owner.email)
        .type(owner.type)
        .nameOfDeceased(owner.nameOfDeceased)
        .businessID(owner.businessID)
        .pending(false);
    };

    self.edit = function() { return self.status(self.statusEdit); };
    self.focusName = function() { $("#neighbors-edit-name").focus(); return self; };

    self.id = ko.observable();
    self.neighborId = ko.observable();
    self.propertyId = ko.observable();
    self.name = ko.observable();
    self.street = ko.observable();
    self.city = ko.observable();
    self.zip = ko.observable();
    self.email = ko.observable();
    self.type = ko.observable();
    self.nameOfDeceased = ko.observable();
    self.businessID = ko.observable();

    self.typeLabel = ko.computed(function() {
        var t = self.type();
        if (t) return loc(['neighbors.owner.type', t]);
        else return null;
    }, self);

    self.editablePropertyId = ko.computed({
        read: function() {
            return util.prop.toHumanFormat(self.propertyId());
        },
        write: function(newValue) {
          if (util.prop.isPropertyId(newValue)) {
            self.propertyId(util.prop.toDbFormat(newValue));
          }
        },
        owner: self
    });


    self.propertyIdOk = ko.computed(function() { return util.prop.isPropertyId(self.propertyId()); });
    self.emailOk = ko.computed(function() { return _.isBlank(self.email()) || util.isValidEmailAddress(self.email()); });
    self.ok = ko.computed(function() { return self.propertyIdOk() && self.emailOk(); });

    self.pending = function(pending) {
      // Should have ajax indicator too
      $("#dialog-edit-neighbor button").attr("disabled", pending ? "disabled" : null);
      return self;
    };

    var paramNames = ["id", "neighborId", "propertyId", "name", "street", "city", "zip", "email", "type", "businessID", "nameOfDeceased"];
    function paramValue(paramName) { return self[paramName](); }

    self.openEdit = function() { LUPAPISTE.ModalDialog.open("#dialog-edit-neighbor"); return self; };
    self.save = function() {
      ajax
        .command(self.neighborId() ? "neighbor-update" : "neighbor-add", _.zipObject(paramNames, _.map(paramNames, paramValue)))
        .pending(self.pending)
        .complete(_.partial(repository.load, self.id(), function(v) { if (!v) { LUPAPISTE.ModalDialog.close(); }}))
        .call();
      return self;
    };

    // self.neighbors.push(makeNew(propertyId));

  }

  hub.onPageChange("neighbors", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  repository.loaded(["neighbors"], function(application) {
    if (applicationId === application.id) { model.init(application); }
  });

  $(function() {
    $("#neighbors-content").applyBindings(model);
    $("#dialog-edit-neighbor").applyBindings(editModel).find("form").placeholderize();
    $("#dialog-select-owners").applyBindings(ownersModel);
  });

})();
