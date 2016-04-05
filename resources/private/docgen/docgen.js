var docgen = (function () {
  "use strict";

  var docModels = {};

  function displayDocuments(containerId, application, documents, authorizationModel, options) {
    var containerSelector = "#" + containerId;
    var oldModels = docModels[containerId] ||  [];

    function updateOther(select) {
      var otherId = select.attr("data-select-other-id"),
          other = $("#" + otherId, select.parent().parent());
      other.parent().css("visibility", select.val() === "other" ? "visible" : "hidden");
    }
    function initSelectWithOther(i, e) { updateOther($(e)); }
    function selectWithOtherChanged(event) {
      updateOther($(event.target));
    }

    var isDisabled = options && options.disabled;

    _.each($(".sticky", containerSelector), function(elem) {
        window.Stickyfill.remove(elem);
    });


    while (oldModels.length > 0) {
      oldModels.pop().dispose();
    }
    docModels[containerId] = [];

    var docgenDiv = $(containerSelector).empty();

    _.each(documents, function (doc) {
      var schema = doc.schema;
      var docModel = new DocModel(schema, doc, application, authorizationModel, options);
      docModels[containerId].push(docModel);
      docModel.triggerEvents();

      docgenDiv.append(docModel.element);

      if (schema.info.repeating && !schema.info["no-repeat-button"] && !isDisabled && authorizationModel.ok("create-doc")) {
        var icon = $("<i>", {"class": "lupicon-circle-plus"});
        var span = $("<span>").text( loc(schema.info.name + "._append_label"));
        var btn = $("<button>",
                    {"data-test-id": schema.info.name + "_append_btn", "class": "secondary"})
          .click(function () {
            var self = this;
            ajax
              .command("create-doc", { schemaName: schema.info.name, id: application.id, collection: docModel.getCollection() })
              .success(function (resp) {
                var newDocId = resp.doc;
                var newDocSchema = _.cloneDeep(schema);
                newDocSchema.info.op = null;

                // The new document might contain some default values, get them from backend
                ajax.query("document", {id: application.id, doc: newDocId, collection: docModel.getCollection()})
                  .success(function(data) {
                    var newDoc = data.document;
                    newDoc.schema = newDocSchema;

                    lupapisteApp.services.accordionService.addDocument(newDoc);

                    var newDocModel = new DocModel(newDocSchema, newDoc, application, authorizationModel);
                    newDocModel.triggerEvents();

                    $(self).before(newDocModel.element);
                    $(".sticky", newDocModel.element).Stickyfill();
                  })
                  .call();
              })
              .call();
          });
        btn.append( [icon, span]);
        docgenDiv.append(btn);
      }
    });

    $("select[data-select-other-id]", docgenDiv).each(initSelectWithOther).change(selectWithOtherChanged);
    $(".sticky", docgenDiv).Stickyfill();
    window.Stickyfill.rebuild();
  }

  function nonApprovedDocuments() {
    var models = _.concat.apply(null, _.values(docModels));
    return _.filter(models, function(docModel) {
      return !docModel.isApproved();
    });
  }

  return {
    displayDocuments: displayDocuments,
    nonApprovedDocuments: nonApprovedDocuments
  };

})();
