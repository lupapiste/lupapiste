var locationSearch = (function() {
  "use strict";

  var serchPointByAddress = function(requestContext, address, onSuccess, onFail) {
    ajax
      .get("/proxy/get-address")
      .param("query", address)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchPointByPropertyId = function(requestContext, propertyId, onSuccess, onFail) {
    ajax
      .get("/proxy/point-by-property-id")
      .param("property-id", util.prop.toDbFormat(propertyId))
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchPropertyId = function(requestContext, x, y, onSuccess, onFail) {
    ajax
      .get("/proxy/property-id-by-point")
      .param("x", x)
      .param("y", y)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchAddress = function(requestContext, x, y, onSuccess, onFail) {
    ajax
      .get("/proxy/address-by-point")
      .param("x", x)
      .param("y", y)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchOwnersByPropertyId = function(requestContext, propertyId, onSuccess, onFail) {
      ajax
      .query("owners")
      .param("propertyId", propertyId)
      .success(requestContext.onResponse(onSuccess))
      .error(requestContext.onResponse(onFail))
      .call();
  };

  return {
    pointByAddress: serchPointByAddress,
    pointByPropertyId: searchPointByPropertyId,
    propertyIdByPoint: searchPropertyId,
    addressByPoint: searchAddress,
    ownersByPropertyId: searchOwnersByPropertyId
  };
})();
