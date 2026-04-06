const axios = require('axios');
const apiClient = axios.create();
apiClient.interceptors.response.use((response) => {
  if (response.data && response.data.code === 200) {
    return response.data.data;
  }
  return response;
});
// mock adapter
apiClient.interceptors.request.use((config) => {
  config.adapter = async () => {
    return {
      data: { code: 200, message: "OK", data: ["novel1", "novel2"] },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    };
  };
  return config;
});

async function test() {
  const response = await apiClient.get('/test');
  console.log("Returned from axios:", response);
  console.log("response.data inside API:", response.data);
}
test();
