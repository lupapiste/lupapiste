LUPAPISTE.NeighborsEditDialogModel = function(params) {
  "use strict";
  var self = this;

  self.status = ko.observable();
  self.statusInit         = 0;
  self.statusEdit         = 2;

  self.init = function(n) {
    var neighbor = n || {},
        owner = neighbor.owner || {},
        address = owner.address || {};

    return self
      .status(self.statusInit)
      .id(lupapisteApp.models.application.id())
      .neighborId(neighbor.id)
      .propertyId(util.prop.toHumanFormat( neighbor.propertyId ))
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

  var propertyId = ko.observable();

  self.id = ko.observable();
  self.neighborId = ko.observable();
  self.propertyId = ko.pureComputed({
    read: function() {
      return util.prop.toHumanFormat(propertyId()) || propertyId();
    },
    write: propertyId
  });
  self.name = ko.observable();
  self.street = ko.observable();
  self.city = ko.observable();
  self.zip = ko.observable();
  self.email = ko.observable();
  self.type = ko.observable();
  self.nameOfDeceased = ko.observable();
  self.businessID = ko.observable();
  self.pending = ko.observable(false);

  self.typeLabel = ko.computed(function() {
    var t = self.type();
    if (t) {
      return loc(["neighbors.owner.type", t]);
    } else {
      return null;
    }
  }, self);

  self.propertyIdOk = ko.computed(function() { return util.prop.isPropertyId(self.propertyId());});
  self.emailOk = ko.computed(function() { return _.isBlank(self.email()) || util.isValidEmailAddress(self.email()); });
  self.isSubmitEnabled = ko.pureComputed(function() { return !self.pending() && self.propertyIdOk() && self.emailOk(); });

  self.propertyIdCss = ko.computed( function() {
    var blank = _.isBlank( self.propertyId() );
    return {tip: blank,
            warn: !blank && !self.propertyIdOk()};
  });

  var paramNames = ["id", "neighborId", "name", "street", "city", "zip", "email", "type", "businessID", "nameOfDeceased"];
  function paramValue(paramName) { return self[paramName](); }

  self.openEdit = function() { LUPAPISTE.ModalDialog.open("#dialog-edit-neighbor"); return self; };

  self.save = function() {
    ajax
      .command(self.neighborId() ? "neighbor-update" : "neighbor-add",
               _.defaults(_.zipObject(paramNames, _.map(paramNames, paramValue)),
                         {propertyId: util.prop.toDbFormat( self.propertyId()) }))
      .pending(self.pending)
      .success(function() {
        repository.load(self.id());
        hub.send("close-dialog");
      })
      .call();
    return self;
  };

  self.init(params.neighbor).edit();
};
