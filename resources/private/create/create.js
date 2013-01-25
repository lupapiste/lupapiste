;(function() {
  "use strict";

  var addressModel = address.createModel(function() { window.location.hash = "#!/create-inforequest2"; });

  var part2Model = new function() {
    var self = this;

    self.operation = ko.observable();
    self.message = ko.observable("");
    self.municipality = ko.observable();
    self.municipalityCode = ko.observable();
    self.links = ko.observableArray();
    self.operations = ko.observable(null);
    self.requestType = ko.observable();

    self.showInfoRequestMessage = ko.observable(false);
    self.showCreateButtons = ko.computed(function() { return self.operation() && !self.showInfoRequestMessage(); });

    self.clear = function() {
      return self
        .operation(null)
        .message("")
        .municipality("")
        .municipalityCode(null)
        .requestType(null)
        .showInfoRequestMessage(false);
    };

    self.municipalityCode.subscribe(function(v) {
      self.operations(null).links.removeAll();
      if (!v || v.length === 0) return;
      ajax
        .query("municipality", {municipality: v})
        .success(function (data) {
          if (self.municipalityCode() === v) {
            self.operations(data.operations).links(data.links);
          }
        })
        .fail(function() {
          if (self.municipalityCode() === v) {
            // FIXME
            error("OH-NOES!");
          }
        })
        .call();
    });

    self.proceedToInfoRequest = self.showInfoRequestMessage.bind(self, true);

    self.create = function(infoRequest) {
      ajax.command("create-application", {
        permitType: 'infoRequest',
        infoRequest: infoRequest,
        operation: self.operation()["op"],
        y: addressModel.y(),
        x: addressModel.x(),
        address: addressModel.address(),
        propertyId: addressModel.propertyId(),
        message: self.message(),
        municipality: self.municipalityCode()
      })
      .success(function(data) {
        repository.reloadApplication(data.id);
        window.location.hash = (infoRequest ? "!/inforequest/" : "!/application/") + data.id;
      })
      .call();
    };

    self.createApplication = self.create.bind(self, false);
    self.createInfoRequest = self.create.bind(self, true);

  };
  
  addressModel.municipality.subscribe(part2Model.municipality);
  addressModel.municipalityCode.subscribe(part2Model.municipalityCode);

  function toLink(l) {
    return "<li><a href='" + l.url + "' target='_blank'>" + l.nameFin + "</li>";
  }

  function generateInfo(value) {
    var e = document.createElement("div");
    e.setAttribute("class", "tree-result");
    $(e).html(
        "<p>" + value.text + "</p>" +
        "<ul>" + _.map(part2Model.links(), toLink).join("") + "</ul>");
    return e;
  }

  hub.onPageChange("create", function() {
    addressModel.clear();
    part2Model.clear();
  });
  
  $(function() {

    addressModel.setMap(gis.makeMap("create-inforequest-map").center(404168, 6840000, 1));
    ko.applyBindings(addressModel, $("#create")[0]);
    $("#create-inforequest-search").autocomplete({
      serviceUrl:      "/proxy/find-address",
      deferRequestBy:  500,
      noCache:         true,
      onSelect:        function(value, data) { addressModel.setAddressData(data); }
    });

    var tree = selectionTree.create(
        $("#create-inforequest2 .tree-content"),
        $("#create-inforequest2 .tree-breadcrumbs"),
        part2Model.operation,
        generateInfo);
    part2Model.operations.subscribe(tree.reset);
    ko.applyBindings(part2Model, $("#create-inforequest2")[0]);
  });

})();
