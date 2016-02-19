//
// Provides services for accordion-toolbar component.
//
//
LUPAPISTE.AccordionService = function() {
  "use strict";
  var self = this;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.indicator = ko.observable({});
  ko.computed(function() {
    var resultType = self.indicator().type;
    if (resultType === "saved") {
      hub.send("indicator", {style: "positive"});
    } else if (resultType === "err") {
      hub.send("indicator", {style: "negative"});
    }
  });

  self.documents = ko.observable();
  self.identifierFields = ko.observableArray([]);

  self.setDocuments = function(docs) {
    self.documents(_.map(docs, function(doc) {
      var fields = doc.schema.info["accordion-fields"];
      var data = _.reduce(fields, function(result, path) {
        return _.set(result, path.join("."), ko.observable(util.getIn(doc.data, path.concat("value"))));
      }, {});
      return {docId: doc.id, operation: ko.mapping.fromJS(doc["schema-info"].op), schema: doc.schema, data: data, accordionPaths: fields};
    }));

    var identifiers = _(docs)
      .filter(function(doc) {
        return _.find(doc.schema.body, "identifier"); // finds first
      })
      .map(function(doc) {
        var subSchema = _.find(doc.schema.body, "identifier");
        var key = _.get(subSchema, "name");
        return {docId: doc.id, schema: subSchema, key: key, value: ko.observable(util.getIn(doc, ["data", key, "value"]))};
      })
      .value();
    self.identifierFields(identifiers); // set the fields for each document
  };

  self.getOperation = function(docId) {
    return util.getIn(_.find(self.documents(), {docId: docId}), ["operation"]);
  };

  self.getOperationByOpId = function(opId) {
    return util.getIn(_.find(self.documents(), function(doc) {
      return doc.operation.id && doc.operation.id() === opId;
    }), ["operation"]);
  };

  self.getIdentifier = function(docId) {
    return _.find(self.identifierFields(), {docId: docId});
  };

  self.getDocumentData = function(docId) { return _.find(self.documents(), {docId: docId});};

  self.toggleEditors = function(show) {
    hub.send("accordionToolbar::toggleEditor", {show: show});
  };

  hub.subscribe("accordionService::saveIdentifier", function(event) {
    var docId = event.docId;
    var value = event.value;
    var path = [event.key];
    var doc = _.find(self.identifierFields(), {docId: docId});
    if (doc.value() !== value) {
      lupapisteApp.services.documentDataService.updateDoc(docId, [[path, value]], self.indicator);
      doc.value(value);
    }
  });

  hub.subscribe("accordionService::saveOperationDescription", function(event) {
    var appId = event.appId;
    var operationId = event.operationId;
    var value = event.description;
    var operation = self.getOperationByOpId(operationId);
    if (operation.description() !== value) {
      ajax.command ("update-op-description", {id: appId,
                                              "op-id": operationId,
                                              desc: value})
      .success (function(resp) {
        operation.description(value);
        hub.send("op-description-changed", {appId: appId,
                                            "op-id": operationId,
                                            "op-desc": value});
        util.showSavedIndicator(resp);
      })
      .call();
    }
  });

  hub.subscribe("accordionUpdate", function(event) {
    var eventPath = event.path;
    var value = event.value;
    var docId = event.docId;

    var documentData = self.getDocumentData(docId);

    var accordionDataObservable = _.get(documentData.data, _.words(eventPath, "."));
    accordionDataObservable(value);

  });
};
