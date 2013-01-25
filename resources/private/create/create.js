;(function() {
  "use strict";


  var model = new function() {
    var self = this;

    self.search = address.createModel();

    self.address = ko.observable();
    
    self.operation = ko.observable();
    self.message = ko.observable("");
    self.links = ko.observableArray();
    self.operations = ko.observable(null);
    self.requestType = ko.observable();
    
    self.showPart2 = ko.observable(false);
    
    self.showInfoRequestMessage = ko.observable(false);
    self.showCreateButtons = ko.computed(function() { return self.operation() && !self.showInfoRequestMessage(); });

    self.clear = function() {
      self.search.clear()
      return self
        .operation(null)
        .message("")
        .requestType(null)
        .showInfoRequestMessage(false);
    };

    self.search.municipalityCode.subscribe(function(v) {
      self.operations(null).links.removeAll();
      if (!v || v.length === 0) return;
      ajax
        .query("municipality", {municipality: v})
        .success(function (data) { if (self.municipalityCode() === v) self.operations(data.operations).links(data.links); })
        .call();
    });

    self.proceedToInfoRequest = self.showInfoRequestMessage.bind(self, true);

    self.create = function(infoRequest) {
      ajax.command("create-application", {
        permitType: infoRequest ? "infoRequest" : "buildingPermit", // FIXME: WTF this should be?
        infoRequest: infoRequest,
        operation: self.operation()["op"],
        y: self.search.y(),
        x: self.search.x(),
        address: self.search.address(),
        propertyId: self.search.propertyId(),
        message: self.message(),
        municipality: self.search.municipalityCode()
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

  function toLink(l) {
    return "<li><a href='" + l.url + "' target='_blank'>" + l.nameFin + "</li>";
  }

  function generateInfo(value) {
    var e = document.createElement("div");
    e.setAttribute("class", "tree-result");
    $(e).html(
        "<p>" + value.text + "</p>" +
        "<ul>" + _.map(model.links(), toLink).join("") + "</ul>");
    return e;
  }

  hub.onPageChange("create", model.clear);
  
  $(function() {

    model.search.setMap(gis.makeMap("create-inforequest-map").center(404168, 6840000, 1));
    ko.applyBindings(model, $("#create")[0]);
    
    $("#create-inforequest-search").autocomplete({
      serviceUrl:      "/proxy/find-address",
      deferRequestBy:  500,
      noCache:         true,
      onSelect:        function(value, data) { model.search.setAddressData(data); }
    });

    var tree = selectionTree.create(
        $("#create-inforequest2 .tree-content"),
        $("#create-inforequest2 .tree-breadcrumbs"),
        model.operation,
        generateInfo);
    model.operations.subscribe(tree.reset);
    
  });

})();
