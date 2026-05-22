const { test, expect } = require('@playwright/test');

function uniqueSession(prefix) {
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
  return `${prefix}${suffix}`.replace(/[^A-Za-z0-9]/g, '').slice(0, 20);
}

async function registerSession(request, session) {
  const response = await request.put(`/session/${session}`);
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.succeed || body.success || body.result).toBeTruthy();
}

async function openSessionPage(page, session) {
  await page.addInitScript((storedSession) => {
    const expires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toUTCString();
    window.localStorage.setItem('expires', expires);
    window.localStorage.setItem('session', storedSession);
  }, session);
  await page.goto(`/?session=${session}`);
}

async function enterTradeMode(request, session) {
  const response = await request.post(`/actions/${session}/set/0/select`, {
    data: { id: 0, value: '' },
  });
  expect(response.ok()).toBeTruthy();
  await expect.poll(async () => {
    const location = await request.get(`/variables/${session}/location`);
    return (await location.json()).location;
  }).toBe('trade');
}

test.describe('1. Сессии и авторизация', () => {
  test('идентификатор с запрещенным символом не должен проходить авторизацию', async ({ page }) => {
    await page.goto('/');

    const base = uniqueSession('bad');
    await page.locator('#sessionId').fill(`${base}!`);
    await page.getByRole('button', { name: 'Login' }).click();

    await expect(page.locator('#sessionId')).toBeVisible();
    await expect(page).not.toHaveURL(new RegExp(`session=${base}`));
  });

  test('идентификатор длиной 20 символов должен считаться корректным', async ({ page }) => {
    const session = `s${Date.now().toString(36)}${Math.random().toString(36).slice(2, 16)}`
      .replace(/[^A-Za-z0-9]/g, '')
      .slice(0, 20);
    expect(session).toHaveLength(20);

    await page.goto('/');
    await page.locator('#sessionId').fill(session);
    await page.getByRole('button', { name: 'Login' }).click();

    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  });
});

test.describe('2. Описание модели и режима перемещения', () => {
  test('перемещение на соседнюю клетку не должно применяться раньше завершения секундного таймера', async ({ request }) => {
    const session = uniqueSession('move');
    await registerSession(request, session);

    const beforeResponse = await request.get(`/variables/${session}/location`);
    const before = await beforeResponse.json();

    const moveResponse = await request.post(`/actions/${session}/move`, {
      data: { direction: 1 },
    });
    expect(moveResponse.ok()).toBeTruthy();

    const immediateResponse = await request.get(`/variables/${session}/location`);
    const immediate = await immediateResponse.json();

    expect(immediate.position).toBe(before.position);
  });
});

test.describe('3. Описание режима торговли', () => {
  test('колонка цены должна показывать стоимость в порядке Купить/Продать', async ({ page, request }) => {
    const session = uniqueSession('price');
    await registerSession(request, session);
    await enterTradeMode(request, session);

    const locationResponse = await request.get(`/variables/${session}/location`);
    const location = await locationResponse.json();
    const firstGood = location.goodsDock.find((good) => good.id === 1001) || location.goodsDock[0];

    await openSessionPage(page, session);
    await expect(page.locator('#tradeTable tr')).not.toHaveCount(0);

    const expectedPrice = `${firstGood.buyPrice.toFixed(2)}/${firstGood.sellPrice.toFixed(2)}`;
    const priceCell = page.locator(`#item${firstGood.id}buy`).locator('xpath=ancestor::tr/td[4]');
    await expect(priceCell).toHaveText(expectedPrice);
  });

  test('нулевое количество товара является недопустимой покупкой и должно помечать кнопку красной', async ({ page, request }) => {
    const session = uniqueSession('zero');
    await registerSession(request, session);
    await enterTradeMode(request, session);

    await openSessionPage(page, session);
    await expect(page.locator('#item1001buy')).toBeVisible();

    await page.locator('#itembuy1001cnt').fill('0');
    await page.locator('#itembuy1001cnt').dispatchEvent('change');

    await expect(page.locator('#item1001buy')).toHaveClass(/btn-red/);
  });
});
